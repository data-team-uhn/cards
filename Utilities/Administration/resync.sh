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
# - Subjects are only ever created, never updated. Everything that can change about a subject lives
#   in its forms; the one way an existing subject changes is by gaining a child subject (a new
#   visit), and that child arrives as a row of its own. So a subject is imported by name into its
#   parent, and one that is already on the destination is left completely untouched — no property
#   rewrite, no checkout, no new version, and no risk to the jcr:uuid that its forms and access
#   tokens reference.
# - Forms are posted to their own path with :replace, which replaces same-named answers in place.
#   The node a request is posted to is never removed, so an existing form keeps its jcr:uuid.
# - Nodes that are new to the destination get their own new jcr:uuid values; this is unavoidable.
#
# Limitations: the destination must already have the same /Questionnaires and /SubjectTypes
# content as the source. Deletions do not propagate, at any level: a subject or form deleted on the
# source is not deleted on the destination, and neither is an answer removed from a form nor a
# property removed from a node — the import only ever writes what it is given, and the pass that
# would drop content missing from it is reachable only in a merge mode that no request parameter
# selects. If computed answers are configured,
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

# A form's answers and answer sections are its payload, so forms are serialized `deep`. A subject's
# are not: its child subjects are modified nodes in their own right and come back as their own rows,
# so serializing a subject deep would send every visit twice — once inside its parent and once by
# itself — and the second copy would be refused as already present.
SUBJECT_SELECTORS=".importable.-answerCopy.-labels"
FORM_SELECTORS=".importable.deep.-answerCopy.-labels"

# Fetch one page of modified nodes from the source, as importable JSON.
# The `identify` processor is kept enabled (unlike a plain export) because the import needs each
# node's original name and location, provided by the @path property; all @-prefixed annotations
# are stripped again before importing. The `answerCopy` and `labels` processors are disabled
# because they decorate the output with computed values (copies of answers on subjects, display
# labels on answers) which must not be imported as actual properties.
fetch_page() {
    local homepage="$1" selectors="$2" offset="$3"
    curl -sf -u "$SOURCE_AUTH" --get "$SOURCE/$homepage.paginate.json" \
        --data-urlencode "resourceSelectors=$selectors" \
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
    local homepage="$1" selectors="$2" offset=0 rows page
    local all="[]"
    while : ; do
        page=$(fetch_page "$homepage" "$selectors" "$offset") || { echo "Export from $SOURCE/$homepage failed" >&2; return 1; }
        rows=$(jq '.rows | length' <<< "$page")
        [[ "$rows" -eq 0 ]] && break
        all=$(jq --argjson new "$(jq '.rows' <<< "$page")" '. + $new' <<< "$all")
        offset=$((offset + rows))
    done
    jq 'sort_by(."@path" | split("/") | length)' <<< "$all"
}

# Each node is imported in a single request that builds it, its properties and its whole subtree in
# one commit. That is not merely fewer requests: preparing the node in an earlier request is what
# made answers duplicate. A form committed with a questionnaire but no answers is exactly what makes
# CreateMissingAnswersEditor generate a complete blank answer tree of its own, under randomly
# generated node names that never collide with the imported ones, leaving the form holding two sets
# of answers — one blank, one real. Sending everything in one commit leaves that editor nothing to
# fill in.
#
# How the request is addressed differs between subjects and forms; see each function below.
# :autoCheckout allows writing to nodes the versioning mechanism has checked in. --form-string
# rather than -F throughout: -F reads a leading @ or < in a value as a file reference, and
# repository data is not under our control.

# Strip the @-prefixed serialization annotations, and the denormalized `form` property of answers
# and answer sections, which holds the jcr:uuid of the form on the SOURCE instance — the destination
# recomputes it from its own form.
import_content() {
    jq 'walk(if type == "object" then
            with_entries(select(.key | startswith("@") | not))
            | (if has("jcr:reference:question") or has("jcr:reference:section") then del(.form) else . end)
        else . end)' <<< "$1"
}

# A subject is imported into its parent under an explicit :name, which is what makes the import
# create the node itself, taking its primary type from the content. :replace=false then means the
# import refuses a node that is already there, answering 412 — and since subjects are never
# updated, that refusal is exactly the outcome we want, not an error.
#
# Do NOT send a jcr:primaryType parameter here: request parameters apply to the resource the
# request was posted to, so it would try to retype the parent — /Subjects itself.
import_subject() {
    local json="$1" nodepath parent name response
    nodepath=$(jq -r '."@path"' <<< "$json")
    parent="${nodepath%/*}"
    name="${nodepath##*/}"
    response=$(curl -s -o /dev/null -w "%{http_code}" -u "$DESTINATION_AUTH" "$DESTINATION$parent/" \
        --form-string ":name=$name" \
        --form-string ":operation=import" \
        --form-string ":contentType=json" \
        --form-string ":replace=false" \
        --form-string ":autoCheckout=true" \
        --form-string ":content=$(import_content "$json")")
    if [[ "$response" -eq 412 ]]; then
        echo "  already present, left alone: $nodepath"
        return 0
    fi
    if [[ "$response" -lt 200 || "$response" -ge 300 ]]; then
        echo "  FAILED ($response): $nodepath" >&2
        return 1
    fi
    echo "  created: $nodepath"
}

# A form is posted to its own path, for two reasons. POST on /Forms is shadowed by the CSV-oriented
# DataImportServlet, so the parent is not available; and posting to the node itself keeps the
# import in parent-node mode, where the node is never removed and so keeps its jcr:uuid — which
# posting by :name would not, since :replace deletes and recreates an existing node.
#
# That mode does mean the import never applies the content's primary type to the form node itself,
# so jcr:primaryType is sent as a request parameter: a missing node is created by Sling's
# deep-create before the content is read, and that step takes the type from the request. A new form
# would come out right even without it, because cards:FormsHomepage declares
# `+ * (cards:Form) = cards:Form`, but that is a property of the parent's node type rather than of
# the import, and is not worth depending on.
import_form() {
    local json="$1" nodepath primarytype response
    nodepath=$(jq -r '."@path"' <<< "$json")
    primarytype=$(jq -r '."jcr:primaryType"' <<< "$json")
    response=$(curl -s -o /dev/null -w "%{http_code}" -u "$DESTINATION_AUTH" "$DESTINATION$nodepath" \
        --form-string "jcr:primaryType=$primarytype" \
        --form-string ":operation=import" \
        --form-string ":contentType=json" \
        --form-string ":replace=true" \
        --form-string ":replaceProperties=true" \
        --form-string ":autoCheckout=true" \
        --form-string ":content=$(import_content "$json")")
    if [[ "$response" -lt 200 || "$response" -ge 300 ]]; then
        echo "  FAILED ($response): $nodepath" >&2
        return 1
    fi
    echo "  imported: $nodepath"
}

failures=0

echo "Exporting subjects modified since $TIMESTAMP from $SOURCE..."
subjects=$(export_modified "Subjects" "$SUBJECT_SELECTORS") || exit 1
count=$(jq 'length' <<< "$subjects")
echo "Importing $count subject(s) into $DESTINATION..."
for i in $(seq 0 $((count - 1))); do
    # Sorted parents-first by the export, so a new visit's parent subject is always there already
    import_subject "$(jq ".[$i]" <<< "$subjects")" || failures=$((failures + 1))
done

echo "Exporting forms modified since $TIMESTAMP from $SOURCE..."
forms=$(export_modified "Forms" "$FORM_SELECTORS") || exit 1
count=$(jq 'length' <<< "$forms")
echo "Importing $count form(s) into $DESTINATION..."
for i in $(seq 0 $((count - 1))); do
    import_form "$(jq ".[$i]" <<< "$forms")" || failures=$((failures + 1))
done

if [[ "$failures" -gt 0 ]]; then
    echo "Re-sync finished with $failures failure(s)" >&2
    exit 1
fi
echo "Re-sync complete"
