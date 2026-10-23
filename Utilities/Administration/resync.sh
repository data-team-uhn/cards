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
# Needs bash 4 or later, curl, and jq 1.6 or later.
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
# - When both instances have been importing data on their own, each holds its own form for the same
#   visit, under a different name. A source form missing from the destination is then imported into
#   the destination's form for the same subject and questionnaire, when that questionnaire allows only
#   one form per subject, instead of being refused as one form too many. Its answers are renamed after
#   the destination's answers to the same questions first, so they replace those answers rather than
#   sitting beside them.
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
    curl -k -sf -u "$SOURCE_AUTH" --get "$SOURCE/$homepage.paginate.json" \
        --data-urlencode "resourceSelectors=$selectors" \
        --data-urlencode "includeallstatus=true" \
        --data-urlencode "offset=$offset" \
        --data-urlencode "limit=$PAGE_SIZE" \
        --data-urlencode "filternames=cards:LastModified" \
        --data-urlencode "filtercomparators=>=" \
        --data-urlencode "filtervalues=$TIMESTAMP" \
        --data-urlencode "filtertypes=datetime"
}

# Export all pages of modified nodes under the given homepage into the file named by $3, one compact
# JSON row per line, sorted by path depth. That ordering is load-bearing: a child subject is only
# ever created by its own row, so its parent has to be imported first.
#
# Rows go through a file rather than a shell variable because this data has no useful upper bound —
# a page of a hundred deep forms is megabytes of JSON. Passing that to jq as an argument overruns
# the kernel's limit on the size of a command line ("Argument list too long"), and accumulating it
# in a variable would additionally re-parse and re-print everything gathered so far on every page.
# Every jq here is checked. When this step failed it used to leave an empty result behind and still
# report success, so the run announced "0 form(s)" and "Re-sync complete" having copied nothing —
# the one failure mode a sync tool must never have.
export_modified() {
    local homepage="$1" selectors="$2" outfile="$3" offset=0 rows page unsorted
    unsorted=$(mktemp) || return 1
    while : ; do
        page=$(fetch_page "$homepage" "$selectors" "$offset") \
            || { echo "Export from $SOURCE/$homepage failed" >&2; rm -f "$unsorted"; return 1; }
        rows=$(jq '.rows | length' <<< "$page") \
            || { echo "Unreadable response from $SOURCE/$homepage at offset $offset" >&2; rm -f "$unsorted"; return 1; }
        [[ "$rows" -eq 0 ]] && break
        jq -c '.rows[]' <<< "$page" >> "$unsorted" \
            || { echo "Failed to collect rows from $SOURCE/$homepage at offset $offset" >&2; rm -f "$unsorted"; return 1; }
        offset=$((offset + rows))
    done
    jq -sc 'sort_by(."@path" | split("/") | length) | .[]' "$unsorted" > "$outfile" \
        || { echo "Failed to sort the exported $homepage" >&2; rm -f "$unsorted"; return 1; }
    rm -f "$unsorted"
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
# :autoCheckout allows writing to nodes the versioning mechanism has checked in.
#
# --form-string rather than -F for every value that carries repository data: -F reads a leading @
# or < in a value as a file reference, and that data is not under our control. The one exception is
# :content, which is deliberately passed as `-F ":content=<file"`. That is our own redirection, not
# something a value could smuggle in, and it keeps a form's serialized subtree off the command line,
# where a big enough one would run into the same argument-size limit as the export used to.

# A repository path as a URL path: each segment percent-encoded, since questionnaire names hold spaces.
url_path() {
    jq -rn --arg path "$1" '$path | split("/") | map(@uri) | join("/")'
}

# How many forms each questionnaire allows per subject on the destination, 0 for no limit.
declare -A MAX_PER_SUBJECT

# Print the path of the form the destination already holds for the same subject and questionnaire as
# the given source form, when the source form is not on the destination itself and its questionnaire
# allows only one form per subject. Prints nothing when the form should be imported at its own path.
#
# /query resolves a path compared with a reference property into the uuid the property holds, so the
# paths in the export serve as they are. It URL-decodes the query once more after the servlet has, so
# the query is encoded once before curl encodes it again.
destination_twin() {
    local json="$1" nodepath="$2" subject questionnaire max query response
    response=$(curl -k -s -I -o /dev/null -w "%{http_code}" -u "$DESTINATION_AUTH" "$DESTINATION$nodepath.json")
    [[ "$response" -eq 404 ]] || return 0
    subject=$(jq -r '."jcr:reference:subject" // empty' <<< "$json")
    questionnaire=$(jq -r '."jcr:reference:questionnaire" // empty' <<< "$json")
    [[ -n "$subject" && -n "$questionnaire" ]] || return 0
    if [[ -z "${MAX_PER_SUBJECT[$questionnaire]+set}" ]]; then
        max=$(curl -k -sf -u "$DESTINATION_AUTH" "$DESTINATION$(url_path "$questionnaire").json" \
            | jq -r '.maxPerSubject // 0') \
            || { echo "  could not read $questionnaire on the destination" >&2; return 1; }
        MAX_PER_SUBJECT[$questionnaire]="$max"
    fi
    [[ "${MAX_PER_SUBJECT[$questionnaire]}" -eq 1 ]] || return 0
    # A single quote inside a JCR-SQL2 string literal is written twice
    query="select * from [cards:Form] as f where f.subject = '${subject//\'/\'\'}'"
    query+=" and f.questionnaire = '${questionnaire//\'/\'\'}'"
    curl -k -sf -u "$DESTINATION_AUTH" --get "$DESTINATION/query" \
        --data-urlencode "query=$(jq -rn --arg query "$query" '$query | @uri')" \
        --data-urlencode "limit=1" \
        | jq -r '.rows[0]."@path" // empty' \
        || { echo "  could not look for the destination's own form for $nodepath" >&2; return 1; }
}

# Rename the answers and answer sections of the form on stdin after those of the destination form in
# the file named by $1, matched by the question or section they belong to: the n-th of the source's
# answers to a question takes the name of the n-th of the destination's. A source answer with no
# counterpart keeps its name, and is added.
align_answers() {
    jq --slurpfile dst "$1" '
        def ref: if type == "object" then (."jcr:reference:question" // ."jcr:reference:section") else null end;
        def align($d):
            if type != "object" then . else
                [($d // {}) | to_entries[] | select(.value | ref != null) | {r: (.value | ref), k: .key}] as $names
                | reduce to_entries[] as $e ({out: {}, seen: {}};
                    ($e.value | ref) as $r
                    | if $r == null then .out[$e.key] = $e.value
                      else ((.seen[$r] // 0) as $i
                          | ([$names[] | select(.r == $r) | .k][$i]) as $k
                          | .seen[$r] = $i + 1
                          | .out[$k // $e.key] = ($e.value | align(if $k then $d[$k] else null end)))
                      end)
                | .out
            end;
        align($dst[0])'
}

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
    local json="$1" nodepath parent name response contentfile
    nodepath=$(jq -r '."@path"' <<< "$json")
    parent="${nodepath%/*}"
    name="${nodepath##*/}"
    contentfile=$(mktemp) || return 1
    import_content "$json" > "$contentfile"
    response=$(curl -k -s -o /dev/null -w "%{http_code}" -u "$DESTINATION_AUTH" "$DESTINATION$parent/" \
        --form-string ":name=$name" \
        --form-string ":operation=import" \
        --form-string ":contentType=json" \
        --form-string ":replace=false" \
        --form-string ":autoCheckout=true" \
        -F ":content=<$contentfile")
    rm -f "$contentfile"
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
    local json="$1" nodepath target twin existing primarytype response contentfile
    nodepath=$(jq -r '."@path"' <<< "$json")
    primarytype=$(jq -r '."jcr:primaryType"' <<< "$json")
    twin=$(destination_twin "$json" "$nodepath") || { echo "  FAILED: $nodepath" >&2; return 1; }
    target="${twin:-$nodepath}"
    contentfile=$(mktemp) || return 1
    if [[ -n "$twin" ]]; then
        existing=$(curl -k -sf -u "$DESTINATION_AUTH" "$DESTINATION$twin$FORM_SELECTORS.json") \
            && import_content "$existing" > "$contentfile" \
            && import_content "$json" | align_answers "$contentfile" > "$contentfile.aligned" \
            && mv "$contentfile.aligned" "$contentfile" \
            || { echo "  FAILED to match $nodepath to the destination's $twin" >&2
                 rm -f "$contentfile" "$contentfile.aligned"; return 1; }
    else
        import_content "$json" > "$contentfile"
    fi
    response=$(curl -k -s -o /dev/null -w "%{http_code}" -u "$DESTINATION_AUTH" "$DESTINATION$target" \
        --form-string "jcr:primaryType=$primarytype" \
        --form-string ":operation=import" \
        --form-string ":contentType=json" \
        --form-string ":replace=true" \
        --form-string ":replaceProperties=true" \
        --form-string ":autoCheckout=true" \
        -F ":content=<$contentfile")
    rm -f "$contentfile"
    if [[ "$response" -lt 200 || "$response" -ge 300 ]]; then
        echo "  FAILED ($response): $nodepath" >&2
        return 1
    fi
    if [[ -n "$twin" ]]; then
        echo "  imported into the destination's own form $twin: $nodepath"
    else
        echo "  imported: $nodepath"
    fi
}

failures=0
subjects=$(mktemp) || exit 1
forms=$(mktemp) || exit 1
trap 'rm -f "$subjects" "$forms"' EXIT

# The import loops read their rows from a file rather than iterating an array held in a variable:
# one row per line means each node is handled without re-reading the ones before it, and nothing
# ever has to fit on a command line. Redirecting the file into `while` rather than piping it also
# keeps the loop in this shell, so `failures` survives it.

echo "Exporting subjects modified since $TIMESTAMP from $SOURCE..."
export_modified "Subjects" "$SUBJECT_SELECTORS" "$subjects" || exit 1
echo "Importing $(wc -l < "$subjects") subject(s) into $DESTINATION..."
while IFS= read -r row; do
    # Parents come first, so a new visit's parent subject is always in place already
    import_subject "$row" || failures=$((failures + 1))
done < "$subjects"

echo "Exporting forms modified since $TIMESTAMP from $SOURCE..."
export_modified "Forms" "$FORM_SELECTORS" "$forms" || exit 1
echo "Importing $(wc -l < "$forms") form(s) into $DESTINATION..."
while IFS= read -r row; do
    import_form "$row" || failures=$((failures + 1))
done < "$forms"

if [[ "$failures" -gt 0 ]]; then
    echo "Re-sync finished with $failures failure(s)" >&2
    exit 1
fi
echo "Re-sync complete"
