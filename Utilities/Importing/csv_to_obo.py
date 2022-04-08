#
#  Licensed to the Apache Software Foundation (ASF) under one
#  or more contributor license agreements.  See the NOTICE file
#  distributed with this work for additional information
#  regarding copyright ownership.  The ASF licenses this file
#  to you under the Apache License, Version 2.0 (the
#  "License"); you may not use this file except in compliance
#  with the License.  You may obtain a copy of the License at
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing,
#  software distributed under the License is distributed on an
#  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
#  KIND, either express or implied.  See the License for the
#  specific language governing permissions and limitations
# under the License.
#

import csv
import re
import sys

# Convert a csv file into an Obo file
# Run it in the directory containing the csv file to be converted.

# Arguments:
# Title. Will import <Title>.csv to <Title>.obo
# ID Column. The column containing the term ids
# Name Column. The column containing the term names
# Any additional arguments will be interpretted as columns containing parent categories of the term

# Sample usage:
# python3 ./csv_to_obo.py DeviceMasterlist "Device ID" "Device Details" "Device Type" "Manufacturer";


FILE_HEADER = """! Licensed to the Apache Software Foundation (ASF) under one
! or more contributor license agreements.  See the NOTICE file
! distributed with this work for additional information
! regarding copyright ownership.  The ASF licenses this file
! to you under the Apache License, Version 2.0 (the
! "License"); you may not use this file except in compliance
! with the License.  You may obtain a copy of the License at
!
! http://www.apache.org/licenses/LICENSE-2.0
!
! Unless required by applicable law or agreed to in writing,
! software distributed under the License is distributed on an
! "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
! KIND, either express or implied.  See the License for the
! specific language governing permissions and limitations
! under the License.

format-version: 1.2

"""

TITLE = sys.argv[1]
ID_COLUMN = sys.argv[2]
NAME_COLUMN = sys.argv[3]
PARENT_COLUMNS = sys.argv[4:] if len(sys.argv) > 4 else []

duplicate_names = []
created_parents = {}
created_terms = []

prev_id = 0

def get_negative_id():
    global prev_id
    prev_id += 1
    return "-{}".format(prev_id)

def clean_id(id_str):
    return re.sub(r"[^a-zA-Z_\d\\_.-]+", '', id_str)

def clean_name(name):
    result = name
    # Escape any characters that need to be escaped per https://owlcollab.github.io/oboformat/doc/GO.format.obo-1_2.html#S.1.5
    result = result.translate(str.maketrans({
        "\\":  "\\\\",
        "\n":  "\\\n",
        " ":  "\W",
        "\t": "\\\t",
        ":":  "\:",
        ",":  "\,",
        "\"":  "\\\"",
        "(": "\(",
        ")": "\)",
        "[": "\[",
        "]": "\]",
        "{": "\{",
        "}": "\}",
        "@": "\@"
        }))
    return result

def write_term(oboFile, term_id, term_name, parents):
    if term_id in created_terms:
        print("Skipping duplicate term with ID '{}', NAME '{}'".format(term_id, term_name))

    name = term_name
    name_is_duplicate = name in duplicate_names

    if parents is not None:
        for parent in parents:
            parent_name = clean_name(parent["value"])
            if (name_is_duplicate):
                name += " ({})".format(parent_name)
            if not parent_name in created_parents:
                parent_id = get_negative_id()
                write_term(oboFile, parent_id, parent_name, parent["parents"] if "parents" in parent else None)
                created_parents[parent_name] = parent_id



    oboFile.write("""[Term]
id: {}
name: {}
""".format(term_id, name))

    comments = []
    if parents is not None:
        for parent in parents:
            oboFile.write("is_a: {}\n".format(created_parents[clean_name(parent["value"])]))
            comments.append(parent["value"])

    if len(comments) > 0:
        oboFile.write("def: {}\n".format(", ".join(comments)))

    oboFile.write("\n")
    created_terms.append(term_id)

with open(TITLE + '.csv', encoding="utf-8-sig") as csvfile:
    reader = csv.DictReader(csvfile, dialect='excel')
    names = []
    for row in reader:
        term_name = row[NAME_COLUMN]
        if term_name in names:
            duplicate_names.append(term_name)
        else:
            names.append(term_name)

with open(TITLE + '.csv', encoding="utf-8-sig") as csvfile:
    reader = csv.DictReader(csvfile, dialect='excel')
    with open(TITLE + '.obo', 'w') as oboFile:
        oboFile.write(FILE_HEADER)
        for row in reader:
            term_id = row[ID_COLUMN]
            term_name = row[NAME_COLUMN]
            term_parents = []
            for parent_column in PARENT_COLUMNS:
                if len(row[parent_column]) > 0:
                    term_parents.append({
                        "value": row[parent_column],
                        "parents": [{
                            "value": parent_column
                        }]
                    })
            write_term(oboFile, term_id, term_name, term_parents)
