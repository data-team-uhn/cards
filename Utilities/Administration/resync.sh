#!/bin/bash
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

# Re-synchronizes two partially synced CARDS instances: assuming the destination is an older
# snapshot of the source, exports all the Subjects and Forms modified since a given timestamp
# from the source, and re-imports them into the destination.
#
# Usage: resync.sh SOURCE_URL DESTINATION_URL TIMESTAMP
#   e.g. resync.sh http://localhost:8080 http://localhost:8081 2026-01-29T15:58:00.000-04:00
#
# Credentials default to admin:admin and can be overridden with the SOURCE_AUTH and
# DESTINATION_AUTH environment variables (user:password).
#
# How it works:
# - Changed nodes are detected with the .paginate endpoint filtering on jcr:lastModified, and
#   serialized with the `importable` serializer, which strips instance-specific properties
#   (jcr:uuid, jcr:created*, jcr:lastModified*, sling:*, versioning) and turns reference
#   properties into `jcr:reference:<name>` keys holding the referenced node's path.
# - Each exported node is imported with the Sling POST servlet's `import` operation, which
#   resolves those paths back into references on the destination. Questionnaire and question
#   references resolve because questionnaires have the same paths on both instances; subject
#   references resolve because subjects are imported first, keeping their original node names.
# - Subjects are merged (:replaceProperties only) so that their child subjects are preserved;
#   forms use :replace so that their answer nodes exactly match the source, including removed
#   answers. In both cases existing nodes are updated in place, keeping their jcr:uuid.
# - Nodes that are new to the destination get their own new jcr:uuid values; this is unavoidable.
#
# Limitations: the destination must already have the same /Questionnaires and /SubjectTypes
# content as the source; deletions are not propagated; if computed answers are configured,
# consider setting COMPUTED_ANSWERS_DISABLED=true on the destination during the import to avoid
# duplicated computed answers.

set -o pipefail

SOURCE="${1%/}"
DESTINATION="${2%/}"
TIMESTAMP="$3"
SOURCE_AUTH="${SOURCE_AUTH:-admin:admin}"
DESTINATION_AUTH="${DESTINATION_AUTH:-admin:admin}"
PAGE_SIZE=100

if [[ -z "$SOURCE" || -z "$DESTINATION" || -z "$TIMESTAMP" ]]; then
    echo "Usage: $0 SOURCE_URL DESTINATION_URL TIMESTAMP" >&2
    echo "  e.g. $0 http://localhost:8080 http://localhost:8081 2026-01-29T15:58:00.000-04:00" >&2
    exit 1
fi

# Fetch one page of modified nodes from the source, as importable JSON.
# The `identify` processor is kept enabled (unlike a plain export) because the import needs each
# node's original name and location, provided by the @path property; all @-prefixed annotations
# are stripped again before importing. The `answerCopy` and `labels` processors are disabled
# because they decorate the output with computed values (copies of answers on subjects, display
# labels on answers) which must not be imported as actual properties.
fetch_page() {
    local homepage="$1" offset="$2"
    curl -sf -u "$SOURCE_AUTH" --get "$SOURCE/$homepage.paginate.json" \
        --data-urlencode "resourceSelectors=.importable.deep.-answerCopy.-labels" \
        --data-urlencode "includeallstatus=true" \
        --data-urlencode "offset=$offset" \
        --data-urlencode "limit=$PAGE_SIZE" \
        --data-urlencode "filternames=cards:LastModified" \
        --data-urlencode "filtercomparators=>=" \
        --data-urlencode "filtervalues=$TIMESTAMP" \
        --data-urlencode "filtertypes=datetime"
}

# Export all pages of modified nodes under the given homepage into one JSON array,
# sorted by path depth so that parent nodes are imported before their descendants.
export_modified() {
    local homepage="$1" offset=0 rows page
    local all="[]"
    while : ; do
        page=$(fetch_page "$homepage" "$offset") || { echo "Export from $SOURCE/$homepage failed" >&2; return 1; }
        rows=$(jq '.rows | length' <<< "$page")
        [[ "$rows" -eq 0 ]] && break
        all=$(jq --argjson new "$(jq '.rows' <<< "$page")" '. + $new' <<< "$all")
        offset=$((offset + rows))
    done
    jq 'sort_by(."@path" | split("/") | length)' <<< "$all"
}

# Import one exported node into the destination, in two steps:
# 1. The node is created (or updated) with its primary type and its single-valued top-level
#    properties, through the regular Sling modify operation. This is needed because the import
#    operation alone deep-creates missing nodes with a default node type (losing referenceability
#    and validity), and applies properties too late for the commit-time validators, which expect
#    a new node to carry its mandatory properties (a form's questionnaire and subject, a
#    subject's identifier and type) from the very beginning.
#    The multi-valued relatedSubjects reference is skipped, the destination recomputes it.
# 2. The full content is imported into the now-valid node with the import operation. The
#    @-prefixed serialization annotations are stripped, as is the denormalized `form` property of
#    answers and answer sections, which holds the jcr:uuid of the form on the source instance —
#    the destination recomputes it with the destination form's own uuid when the answers are
#    saved. The import is posted directly to the node's own path; posting to the parent with
#    :name would be shadowed by the CSV-oriented DataImportServlet bound to POST on /Forms.
# :autoCheckout allows updating forms that are checked in by the versioning mechanism.
import_node() {
    local json="$1" replace="$2"
    local nodepath content response primarytype
    local -a prepare
    nodepath=$(jq -r '."@path"' <<< "$json")
    primarytype=$(jq -r '."jcr:primaryType"' <<< "$json")
    prepare=(-F "jcr:primaryType=$primarytype")
    while IFS=$'\t' read -r prop value; do
        [[ -z "$prop" ]] && continue
        if [[ "$prop" == jcr:reference:* ]]; then
            prepare+=(-F "${prop#jcr:reference:}=$value" -F "${prop#jcr:reference:}@TypeHint=Reference")
        else
            prepare+=(-F "$prop=$value")
        fi
    done < <(jq -r 'to_entries[]
        | select((.value | type) as $t | $t == "string" or $t == "number" or $t == "boolean")
        | select((.key | startswith("@") or . == "jcr:primaryType") | not)
        | [.key, (.value | tostring)] | @tsv' <<< "$json")
    response=$(curl -s -o /dev/null -w "%{http_code}" -u "$DESTINATION_AUTH" "$DESTINATION$nodepath" \
        "${prepare[@]}" -F ":autoCheckout=true")
    if [[ "$response" -lt 200 || "$response" -ge 300 ]]; then
        echo "  FAILED ($response) to prepare: $nodepath" >&2
        return 1
    fi
    content=$(jq 'walk(if type == "object" then
            with_entries(select(.key | startswith("@") | not))
            | (if has("jcr:reference:question") or has("jcr:reference:section") then del(.form) else . end)
        else . end)' <<< "$json")
    response=$(curl -s -o /dev/null -w "%{http_code}" -u "$DESTINATION_AUTH" "$DESTINATION$nodepath" \
        -F ":operation=import" \
        -F ":contentType=json" \
        -F ":replace=$replace" \
        -F ":replaceProperties=true" \
        -F ":autoCheckout=true" \
        -F ":content=$content")
    if [[ "$response" -lt 200 || "$response" -ge 300 ]]; then
        echo "  FAILED ($response): $nodepath" >&2
        return 1
    fi
    echo "  imported: $nodepath"
}

failures=0

echo "Exporting subjects modified since $TIMESTAMP from $SOURCE..."
subjects=$(export_modified "Subjects") || exit 1
count=$(jq 'length' <<< "$subjects")
echo "Importing $count subject(s) into $DESTINATION..."
for i in $(seq 0 $((count - 1))); do
    # Subjects are merged, not replaced, to preserve their jcr:uuid, which existing forms and
    # access tokens on the destination reference
    import_node "$(jq ".[$i]" <<< "$subjects")" "false" || failures=$((failures + 1))
done

echo "Exporting forms modified since $TIMESTAMP from $SOURCE..."
forms=$(export_modified "Forms") || exit 1
count=$(jq 'length' <<< "$forms")
echo "Importing $count form(s) into $DESTINATION..."
for i in $(seq 0 $((count - 1))); do
    import_node "$(jq ".[$i]" <<< "$forms")" "true" || failures=$((failures + 1))
done

if [[ "$failures" -gt 0 ]]; then
    echo "Re-sync finished with $failures failure(s)" >&2
    exit 1
fi
echo "Re-sync complete"
