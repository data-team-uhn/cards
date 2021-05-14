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

import enum
import json
import csv
import re
import regex

class RowTypes(enum.Enum):
    DEFAULT = 0
    SECTION = 1
    SECTION_RECURRENT = 2
    SECTION_CONDITIONAL = 3
    DATE = 4
    TIME = 5
    NUMBER = 6
    TEXT = 7
    LIST = 8
    CALCULATED = 9

SECTION_TYPES = [RowTypes.SECTION, RowTypes.SECTION_RECURRENT, RowTypes.SECTION_CONDITIONAL]
CONDITION_DEFINTIONS = ["if", "displayed if:", "show the field only if:", "show only if:", "show only if", "show the field only if", "show field only if", "show this field only if", "show this field if", "show field if"]
CONDITION_SPLIT = [" = ", "selection was ", " was ", " selection is ", " response is ", " selections is ", " is "]
MULTIPLE_DEFINITIONS = ["select all that apply", "select all", "allow for multiple", "check all"]
UNIT_DEFINITIONS = ["in "]
RANGE_DEFINITIONS = ["valid range:", "validrange:"]
REQUIRED_DEFINITION = ["required field"]
SECTION_PREFIX = "section: "
question_dictionary = {}


class Headers1:
    SECTION = "Section Name"
    QUESTION = "Field / Question"
    NAME = "Short name"
    DEFINITION = "Variable Values"
    CONDITIONS = "Further Instructions"
    CONDITION_QUESTION = True

class Headers1Impact(Headers1):
    SECTION = "Section Name (Rearranged Variables in Non-Bold)"

class Headers1NPC_QIC(Headers1):
    CONDITION_QUESTION = False

class Headers2:
    SECTION = "Section Name"
    QUESTION = "Description"
    NAME = "Client Field"
    DEFINITION = "Type"
    OPTIONS = "Values"
    CONDITIONS = "Constraints"
    CONDITION_QUESTION = False

def partition_ignore_strings(input, splitter):
    ignore_map = {
        "(": ")",
        "[": "]",
        "{": "}",
        "\"": "\"",
    }
    ignore_list = []
    results = [input]
    i = 0
    while i < len(input):
        if len(ignore_list) > 0 and input[i] == ignore_list[len(ignore_list) - 1]:
            ignore_list.pop()
        elif input[i] in ignore_map.keys():
            ignore_list.append(ignore_map[input[i]])
        elif len(ignore_list) == 0:
            if input[i:i+len(splitter)] == splitter:
                results = [input[0:i], splitter, input[i+len(splitter):]]
                break
        i += 1
    if (len(ignore_list) > 0):
        print("Partition ignore list not cleared for '{}': {}".format(input, ignore_list))
    return results

def split_ignore_strings(input, splitters):
    ignore_map = {
        "(": ")",
        "[": "]",
        "{": "}",
        "\"": "\"",
    }
    ignore_list = []
    results = []
    i = 0
    last_split = 0
    while i < len(input):
        if len(ignore_list) > 0 and input[i] == ignore_list[len(ignore_list) - 1]:
            ignore_list.pop()
        elif input[i] in ignore_map.keys():
            ignore_list.append(ignore_map[input[i]])
        elif len(ignore_list) == 0:
            for splitter in splitters:
                if i + len(splitter) <= len(input):
                    if input[i:i+len(splitter)] == splitter:
                        results.append(input[last_split:i].strip())
                        i += len(splitter)
                        last_split = i
                        break
        i += 1
    results.append(input[last_split:].strip())
    if (len(ignore_list) > 0):
        # print("Split ignore list not cleared for '{}': {}".format(input, ignore_list))
        pass
    return results

def prepare_conditional_string(conditional_string, question):
    # Split statement into two at the 'or'
    if len(partition_ignore_strings(conditional_string, " or ")) > 1:
        conditional_string = partition_ignore_strings(conditional_string, " or ")
        # If one of the resulting statements is incomplete
        # Such as in the case of splitting "if CVLT-C or CVLT-II=yes"
        # Copy what's after the equals sign to the incomplete part
        if "=" not in conditional_string[0]:
            insert_conditional(conditional_string[0] + conditional_string[2].partition("=")[1] + conditional_string[2].partition("=")[2], question, '1')
        else:
            insert_conditional(conditional_string[0], question, '1')
        insert_conditional(conditional_string[2], question, '2')
    else:
        # No title is needed because only a single lfs:Conditional will be created
        insert_conditional(conditional_string, question, '')

# Updates the question with lfs:Conditionals from the output of prepare_conditional
def insert_conditional(conditional_string, question, title):
    # Split the conditional into two operands and an operator
    conditional_string = partition_conditional_string(conditional_string)
    operand_a = conditional_string[0].strip()
    operator = conditional_string[1]
    operand_b = conditional_string[2].strip()
    # If the first operand is a comma-separated list, create a separate conditional for each
    # Enclose the conditionals in a lfs:ConditionalGroup
    if ',' in operand_a:
        question.update({'conditionalGroup': {'jcr:primaryType': 'lfs:ConditionalGroup'}})
        # The keyword 'all' in the conditional string should correspond to 'requireAll' == true
        # If it is present, remove it from the operand and add 'requireAll' to the conditional group
        if 'all' in operand_a:
            operand_a = operand_a[:-3]
            question['conditionalGroup'].update({'requireAll': True})
        operand_a_list = list(operand_a.split(','))
        for index, item in enumerate(operand_a_list):
            question['conditionalGroup'].update(create_conditional(item, operator, operand_b, 'condition' + str(index)))
    # If the second operand is a comma-separated list, create a separate conditional for each
    # Enclose the conditionals in a lfs:ConditionalGroup
    elif ',' in operand_b or ' or ' in operand_b:
        question.update({'conditionalGroup': {'jcr:primaryType': 'lfs:ConditionalGroup'}})
        # The keyword 'all' in the conditional string should correspond to 'requireAll' == true
        # If it is present, remove it from the operand and add 'requireAll' to the conditional group
        if 'all' in operand_b:
            operand_b = operand_b[:-3]
            question['conditionalGroup'].update({'requireAll': True})
        operand_b_list = split_ignore_strings(operand_b, [", ", " or "])
        for index, item in enumerate(operand_b_list):
            question['conditionalGroup'].update(create_conditional(operand_a, operator, item, 'condition' + str(index)))
    else:
        question.update(create_conditional(operand_a, operator, operand_b, 'condition' + title))

# Adds a minAnswers property if 'MissingData' contains the keyword 'illegal'
def insert_min_answers(question, row):
    question.update({'minAnswers': 1})


def options_list(categorical_list):
    if '\n' in categorical_list:
        option_list = categorical_list.splitlines()
    else:
        split_character = ','
        if '(' in categorical_list:
            categorical_list = categorical_list.replace(')', '')
            categorical_list = categorical_list.replace('(', '')
        if '/' in categorical_list and not ',' in categorical_list:
            split_character = '/'
        if ';' in categorical_list:
            split_character = ';'
        option_list = list(categorical_list.split(split_character))
    return option_list

# Split the conditional_string entry into 3 parts: The first operand, the operator and the second operand.
def partition_conditional_string(conditional_string):
    match = regex.search('=|<>|<|>', conditional_string)
    if not match:
        # No operator detected, return everything as a single operand
        print("Failed to parse conditional: " + conditional_string)
        return conditional_string, '', ''

    seperator = match.group(0)
    parts = regex.split(seperator, conditional_string, 1)
    return parts[0], seperator, parts[1]

# Returns a dict object that is formatted as an lfs:Conditional
def create_conditional(operand_a, operator, operand_b, title):
    operand_a_updated = operand_a
    if Headers.CONDITION_QUESTION:
        short_name = question_dictionary.get(operand_a_updated.lower(), "")
        if short_name != "":
            operand_a_updated = short_name
        else:
            if operand_b == "":
                # print("Invalid operand_b for '{}' {} '{}'".format(operand_a_updated, operator, operand_b))
                pass
            else:
                # TODO: Verify that no questions meet this condition, as that means there is a typo
                # print("Could not find question for '{}' {} '{}'".format(operand_a_updated, operator, operand_b))
                pass
    is_reference = False
    # NOTE: IN THE CASE OF A REFRENCE TO A QUESTION WHOSE POSSIBLE VALUES ARE YES/NO/OTHER
    # YOU WILL HAVE TO MANUALLY CHANGE THE CONDITIONALS SINCE THEY WILL BE REPLACED WITH T/F
    if operand_b.lower() == 'yes':
        operand_b_updated = "1"
    elif operand_b.lower() == 'no':
        operand_b_updated = "0"
    else:
        operand_b_updated = operand_b
    result = {
        'jcr:primaryType': 'lfs:Conditional',
        'operandA': {
            'jcr:primaryType': 'lfs:ConditionalValue',
            'value': [operand_a_updated.lower()],
            'isReference': True
        },
        'comparator': operator,
        'operandB': {
            'jcr:primaryType': 'lfs:ConditionalValue',
            'value': [operand_b_updated],
            'isReference': is_reference
        }
    }
    # If the operator is <>, make sure that all entries for operand_a meet that requirement
    if (operator == "<>"):result['operandA']['requireAll'] = True
    return {title: result}


def get_row_type(row):
    row_type = RowTypes.DEFAULT
    if row[Headers.DEFINITION]:
        variable = row[Headers.DEFINITION].lower()
        if ("mm" in variable and "yyyy" in variable):
            row_type = RowTypes.DATE
        elif ("hh" in variable and "mm" in variable):
            # Time must be checked after date, as datetimes should be parsed as dates
            row_type = RowTypes.TIME
        elif ("numeric" in variable):
            row_type = RowTypes.NUMBER
        elif ("\n" in variable):
            row_type = RowTypes.LIST
        elif ("text field" == variable):
            row_type = RowTypes.TEXT
        elif ("calculated" in variable):
            row_type = RowTypes.CALCULATED
        elif ("section" == variable):
            row_type = RowTypes.SECTION
        elif ("conditional" in variable):
            row_type = RowTypes.SECTION_CONDITIONAL
        elif ("recurrent" in variable):
            row_type = RowTypes.SECTION_RECURRENT
        else:
                # print("Unexpected Variable Values \"{}\" found for question \"{}\"".format(row[Headers.DEFINITION],row[Headers.NAME]))
                pass
    elif row[Headers.QUESTION]:
        # print("No variable type determined for question \"{}\"".format(row[Headers.QUESTION]))
        pass
    else:
        if (row[Headers.NAME] and SECTION_PREFIX in row[Headers.NAME].lower()):
            row_type = RowTypes.SECTION
        else:
            # Uncomment to check if any data is being lost
            # print("Skipped row {}".format(row))
            pass
    return row_type

def clean_title(title):
    multiple_visits = " - Study Visits (# = "
    multiple_types = " ("
    result = title
    if multiple_visits in title:
        result = title[:title.index(multiple_visits)]
    if multiple_types in result:
        result = result[:result.index(multiple_types)]
    if SECTION_PREFIX in title.lower():
        result = result[len(SECTION_PREFIX):]
        # print("Replaced \"{}\" with \"{}\"".format(title, result))
    return result.strip().replace('/','')

def row_starts_section(row, row_type):
    return row_type in SECTION_TYPES or (Headers.SECTION in row and row[Headers.SECTION])

section_index = 0

def get_section_title(row):
    if (Headers.SECTION in row and row[Headers.SECTION]):
        return row[Headers.SECTION]
    elif (row[Headers.NAME]):
        return row[Headers.NAME]
    else:
        # print("Section missing name {}".format(row))
        global section_index
        section_index += 1
        return "Section{}".format(section_index)

def start_questionnaire(title):
    questionnaire = {}
    questionnaire['jcr:primaryType'] = 'lfs:Questionnaire'
    questionnaire['title'] = clean_title(title)
    questionnaire['jcr:reference:requiredSubjectTypes'] = ["/SubjectTypes/Patient"]
    return questionnaire

def start_section(questionnaire, section, row):
    section = end_section(questionnaire, section)
    section['jcr:primaryType'] = 'lfs:Section'
    section['label'] = clean_title(get_section_title(row))
    return section

def end_section(questionnaire, section):
    if len(section) > 0:
        questionnaire[section['label']] = dict.copy(section)
    return {}

def insert_recurrent(parent, row):
    parent['recurrent'] = True

def insert_date(question, row):
    date = row[Headers.DEFINITION]
    date = date.replace("D", "d").replace("Y", "y").replace("-", "/")
    question['dateFormat'] = date
    question['dataType'] = "date"

def process_conditions(question, condition_text, title):
    conditions = re.split(';\s*', condition_text)
    for condition in conditions:
        process_split_conditions(question, condition, title)

def process_split_conditions(question, condition, title):
    lower = condition.lower()
    for starter in CONDITION_DEFINTIONS:
        if lower.startswith(starter):
            stripped_condition = condition[len(starter):].strip()
            for splitter in CONDITION_SPLIT:
                if splitter in stripped_condition:
                    stripped_condition = stripped_condition.replace(splitter, " = ")
            if " = " in stripped_condition:
                prepare_conditional_string(stripped_condition, question)
                return
    for starter in MULTIPLE_DEFINITIONS:
        if starter in lower:
            # TODO: Handle select
            # TODO: Handle starter + condition
            # print(lower)
            return
    for starter in UNIT_DEFINITIONS:
        if lower.startswith(starter):
            stripped_condition = lower[len(starter):].strip()
            question["Units"] = stripped_condition
            return
    for starter in RANGE_DEFINITIONS:
        if lower.startswith(starter):
            stripped_condition = lower[len(starter):].strip()
            value_range = stripped_condition.split("-")
            question["minValue"] = value_range[0]
            question["maxValue"] = value_range[1]
            return
    if "description" in question:
        question["description"] = question["description"] + ". " + condition
    else:
        question["description"] = condition

def insert_question(parent, row, question, row_type):
    text = row[Headers.QUESTION].strip() or question
    dividers = []
    if text[len(text) - 1] == "]" and " [" in text:
        dividers = [" [", "]"]
    elif text[len(text) - 1] == ")" and " (" in text:
        dividers = [" (", ")"]
    if (len(dividers) == 2):
        description = text[text.rindex(dividers[0]) + 2 : len(text) - 1]
        text = text[:text.rindex(dividers[0])].strip()
        parent[question] = {
            'jcr:primaryType': 'lfs:Question',
            'text': text,
            'description': description,
        }
    else:
        parent[question] = {
            'jcr:primaryType': 'lfs:Question',
            'text': text,
        }

    question_dictionary[text.lower()] = question
    insert_question_type(row, parent[question], row_type)

def insert_question_type(row, question, row_type):
    if (row_type == RowTypes.DATE):
        question['dataType'] = "date"
    elif (row_type == RowTypes.TIME):
        question['dataType'] = "time"
    elif (row_type == RowTypes.NUMBER):
        question['dataType'] = "decimal"
    elif (row_type == RowTypes.TEXT):
        question['dataType'] = "text"
    elif (row_type == RowTypes.LIST):
        question['dataType'] = "text"
        insert_list(row, question)
    elif (row_type == RowTypes.CALCULATED):
        question['dataType'] = "computed"

def insert_list(row, question):
    option_list = options_list(row[Headers.DEFINITION])
    question.update({'displayMode': 'list'})
    for option in option_list:
        if len(option) == 0:
            # Empty option, skip
            continue
        value = option
        if option.lower().strip() == "other":
            question.update({'displayMode': 'list+input'})
        elif '=' in option:
            options = option.split('=')
            label = options[1].strip()
            option_details = {
                'jcr:primaryType': 'lfs:AnswerOption',
                'label': label,
                'value': options[0].strip()
            }
            answer_option = {options[0].strip().replace("/", "-"):
                add_option_properties(option_details, label)
            }
            question.update(answer_option)
        else:
            option_details = {
                'jcr:primaryType': 'lfs:AnswerOption',
                'label': value.strip(),
                'value': value.strip()
            }
            answer_option = {option.replace("/", "-").strip():
                add_option_properties(option_details, value)
            }
            question.update(answer_option)

def add_option_properties(option, label):
    base_label = label.lower().strip()
    if base_label == "none of the above":
        option['noneOfTheAbove'] = True
    if base_label == "n/a" or base_label == "not applicable" or base_label == "none":
        option['notApplicable'] = True
    return option

# Creates a JSON file that contains the tsv file as an lfs:Questionnaire
def csv_to_json(title):
    questionnaire = start_questionnaire(title)
    section = {}

    with open(title + '.csv', encoding="utf-8-sig") as csvfile:
        reader = csv.DictReader(csvfile, dialect='excel')
        for row in reader:
            row_type = get_row_type(row)
            if (row_starts_section(row, row_type)):
                section = start_section(questionnaire, section, row)
                if (row_type == RowTypes.SECTION_CONDITIONAL):
                    # TODO Are there cases where this is actually needed?
                    # Most of the conditional sections have the conditions on their questions anyways
                    # insert_conditional(parent, row)
                    continue
                elif (row_type == RowTypes.SECTION_RECURRENT):
                    insert_recurrent(parent, row)
                    continue
                elif (row_type == RowTypes.SECTION):
                    continue
            parent = section if len(section) > 0 else questionnaire
            question = row[Headers.NAME].strip().lower()
            if (len(question) == 0):
                # print("Skipped row missing question {}".format(row))
                continue
            elif ("\n" in question):
                # print("Skipped row with newline in name {}".format(question))
                continue
            insert_question(parent, row, question, row_type)
            if question and Headers.CONDITIONS in row and row[Headers.CONDITIONS].lower().endswith("other"):
                # Skip this row as the previous list should have an "other" text field
                continue
            if question and question.endswith("_#"):
                question = question[:len(question) - 2]


            # TODO: Add to Headers?
            # Not used for new imports, kept in for compatibility with cardiac_rehab
            if 'Description' in row and row['Description'] != '':
                parent[question]['description'] = row['Description']
            if 'Units' in row and row['Units'] != '':
                parent[question]['unitOfMeasurement'] = row['Units']
            if 'Min Value' in row and row['Min Value']:
                parent[question]['minValue'] = float(row['Min Value'])
            if 'Max Value' in row and row['Max Value']:
                parent[question]['maxValue'] = float(row['Max Value'])
            if row[Headers.QUESTION].endswith("(single)"):
                parent[question]['maxAnswers'] = 1
            if 'Compact' in row and row['Compact'] != '':
                value = row['Compact']
                if value[0].lower() == "y":
                    parent[question]['compact'] = True
            # End unused section

            # Response Required should be the last conditional property.
            # Otherwise, parent[question] may error out if a conditional section has been created
            if row_type == RowTypes.LIST and Headers.CONDITIONS in row and row[Headers.CONDITIONS]:
                conditional = row[Headers.CONDITIONS]
                if conditional[0].lower() == "y":
                    insert_min_answers(parent[question], row)
                process_conditions(parent[question], conditional, question)
                # TODO: Reimplement after condition rework
                # if len(value) > 4 and value[2:4].lower() == "if":
                #     previous_data = parent[question]
                #     parent.update({question + 'Section': {
                #         'jcr:primaryType': 'lfs:Section'
                #     }})
                #     parent[question + 'Section'][question] = previous_data
                #     prepare_conditional_string(value [5:], parent[question + 'Section'])
                #     # The presence of a conditional will also prevent the question from being inserted into the main thing
                #     del parent[question]

    end_section(questionnaire, section)

    # for q in questionnaires:
    with open(title + '.json', 'w') as jsonFile:
        json.dump(questionnaire, jsonFile, indent='\t')
    print('python3 lfs/Utilities/JSON-to-XML/json_to_xml.py "' + title +'.json" > "' + title + '.xml";\\')


titles = [
    ['STS Database - Final', Headers1],
    ['PC4 Database - Finalv2', Headers1],
    ['PAC3 Database - Final', Headers1],
    ['cnoc_qol', Headers2],
    ['cnoc_imaging', Headers2],
    ['cnoc_assessments_6_21_age', Headers2],
    ['cnoc_assessments_0_5_age', Headers2],
    ['cnoc_history', Headers2],
    ['IMPACT Database Excel', Headers1Impact],
    ['NPC-QIC Database Excel', Headers1NPC_QIC],
]
Headers = Headers1
for title in titles:
    # Uncomment to divide console logs by spreadsheet
    # print(title[0])
    Headers = title[1]
    csv_to_json(title[0])
