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

import json
import csv
import re
import regex

is_new_form = False

def prepare_conditional_string(conditional_string, question, index=1, require_all_in=False):
    require_all = require_all_in
    # Split statement into two at 'or' or 'and'
    partition_or = partition_ignore_strings(conditional_string, " or ")
    partition_and = partition_ignore_strings(conditional_string, " and ")
    if len(partition_or) > 1 or len(partition_and) > 1:
        conditional_string = []
        if len(partition_or) > 1 and len(partition_and) > 1:
            conditional_string = partition_or if len(partition_or) < len(partition_and) else partition_and
            require_all = True
        elif len(partition_or) > 1:
            conditional_string = partition_or
        else:
            conditional_string = partition_and
            require_all = True

        # If one of the resulting statements is incomplete
        # Such as in the case of splitting "if CVLT-C or CVLT-II=yes"
        # Copy what's after the equals sign to the incomplete part
        first_string = conditional_string[0]
        if "=" not in first_string:
            first_string = conditional_string[0] + conditional_string[2].partition("=")[1] + conditional_string[2].partition("=")[2]
        insert_conditional(first_string, question, str(index), require_all, True)
        prepare_conditional_string(conditional_string[2], question, index + 1, require_all)
    else:
        # No title is needed because only a single lfs:Conditional will be created
        if (index == 1):
            insert_conditional(conditional_string, question, '', require_all)
        else:

            insert_conditional(conditional_string, question, str(index), require_all, True)


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

def split_ignore_strings(input, splitters, limit = -1):
    ignore_map = {
        "(": ")",
        "[": "]",
        "{": "}",
        "\"": "\"",
    }
    ignore_list = []
    results = []
    number_splits = 0
    i = 0
    last_split = 0
    while i < len(input) and (limit == -1 or number_splits <= limit):
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
                        number_splits += 1
                        break
        i += 1
    results.append(input[last_split:].strip())
    if (len(ignore_list) > 0):
        # print("Split ignore list not cleared for '{}': {}".format(input, ignore_list))
        pass
    return results


# Updates the question with lfs:Conditionals from the output of prepare_conditional
def insert_conditional(conditional_string, question, title, require_all=False, group=False):
    # Split the conditional into two operands and an operator
    conditional_string = partition_conditional_string(conditional_string)
    operand_a = conditional_string[0].strip()
    operator = conditional_string[1]
    operand_b = conditional_string[2].strip()
    # If the first operand is a comma-separated list, create a separate conditional for each
    # Enclose the conditionals in a lfs:ConditionalGroup
    if len(split_ignore_strings(operand_a, ',')) > 1:
        if 'conditionalGroup' not in question:
            question.update({'conditionalGroup': {'jcr:primaryType': 'lfs:ConditionalGroup'}})
        # The keyword 'all' in the conditional string should correspond to 'requireAll' == true
        # If it is present, remove it from the operand and add 'requireAll' to the conditional group
        if 'all' in operand_a or require_all:
            question['conditionalGroup'].update({'requireAll': True})
        if 'all' in operand_a:
            operand_a = operand_a[:-3]
        operand_a_list = list(split_ignore_strings(operand_a, ','))
        for index, item in enumerate(operand_a_list):
            index_mod = 0
            while 'condition' + str(index_mod) in question['conditionalGroup']:
                index_mod += 1
            question['conditionalGroup'].update(create_conditional(item, operator, operand_b, 'condition' + str(index + index_mod)))
    # If the second operand is a comma-separated list, create a separate conditional for each
    # Enclose the conditionals in a lfs:ConditionalGroup
    elif len(split_ignore_strings(operand_b, ',')) > 1:
        if 'conditionalGroup' not in question:
            question.update({'conditionalGroup': {'jcr:primaryType': 'lfs:ConditionalGroup'}})
        # The keyword 'all' in the conditional string should correspond to 'requireAll' == true
        # If it is present, remove it from the operand and add 'requireAll' to the conditional group
        if 'all' in operand_b or require_all:
            question['conditionalGroup'].update({'requireAll': True})
        if 'all' in operand_b:
            operand_b = operand_b[:-3]
        operand_b_list = list(split_ignore_strings(operand_b, ','))
        index_mod = 0
        while 'condition' + str(index_mod) in question['conditionalGroup']:
            index_mod += 1
        for index, item in enumerate(operand_b_list):
            question['conditionalGroup'].update(create_conditional(operand_a, operator, item, 'condition' + str(index + index_mod)))
    elif group:
        if 'conditionalGroup' not in question:
            question.update({'conditionalGroup': {'jcr:primaryType': 'lfs:ConditionalGroup'}})
        if require_all:
            question['conditionalGroup'].update({'requireAll': True})
        question['conditionalGroup'].update(create_conditional(operand_a, operator, operand_b, 'condition' + title))
    else:
        question.update(create_conditional(operand_a, operator, operand_b, 'condition' + title))

# Split the conditional_string entry into 3 parts: The first operand, the operator and the second operand.
def partition_conditional_string(conditional_string):
    conditional_string = conditional_string.replace(" is ", " = ")
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
    is_reference = False
    # NOTE: IN THE CASE OF A REFRENCE TO A QUESTION WHOSE POSSIBLE VALUES ARE YES/NO/OTHER
    # YOU WILL HAVE TO MANUALLY CHANGE THE CONDITIONALS SINCE THEY WILL BE REPLACED WITH T/F
    if operand_b.lower() == 'yes':
        operand_b_updated = "1"
    elif operand_b.lower() == 'no':
        operand_b_updated = "0"
    else:
        operand_b_updated = operand_b
    if (len(operand_b_updated) > 1 and operand_b_updated.startswith('"') and operand_b_updated.endswith('"')):
        operand_b_updated = operand_b_updated[1:-1]
    result = {
        'jcr:primaryType': 'lfs:Conditional',
        'operandA': {
            'jcr:primaryType': 'lfs:ConditionalValue',
            'value': [operand_a.lower()],
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


def process_options(question, row):
    if question['dataType'] == "date":
        date = row['Options (if applicable)']
        date = date.replace("D", "d").replace("Y", "y").replace("-", "/")
        question['dateFormat'] = date
    elif question['dataType'] == "time":
        question['dateFormat'] = row['Options (if applicable)']
    elif question['dataType'] == "computed":
        insert_expression(question, row['Options (if applicable)'])
    else:
        insert_options(question, row)

def insert_expression(question, expression):
    control_chars = "+-/* ()"
    neutral_chars = ".0123456789:-"
    start_chars = "@{"
    end_chars = "}"
    was_control = True
    i = 0
    while i < len(expression):
        if not expression[i] in neutral_chars and was_control and not (expression[i] in control_chars):
            was_control = False
            expression = expression[:i] + start_chars + expression[i:]
            i += len(start_chars)
        elif not expression[i] in neutral_chars and not was_control and expression[i] in control_chars:
            was_control = True
            expression = expression[:i] + end_chars + expression[i:]
            i += len(end_chars)
        i += 1
    if not was_control:
        expression += end_chars
    question['expression'] = "return " + expression

# Creates lfs:AnswerOptions from the CSV in 'Categorical List'
def insert_options(question, row):
    option_list = options_list(row['Options (if applicable)'])
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
            answer_option = {clean_name(options[0].strip()):
                add_option_properties(option_details, label)
            }
            question.update(answer_option)
        else:
            option_details = {
                'jcr:primaryType': 'lfs:AnswerOption',
                'label': value.strip(),
                'value': value.strip()
            }
            answer_option = {clean_name(option.strip()):
                add_option_properties(option_details, value)
            }
            question.update(answer_option)

def add_option_properties(option, label):
    base_label = label.lower().strip()
    if base_label in ["none of the above", "no"]:
        option['noneOfTheAbove'] = True
    if base_label  in ["n/a", "not applicable", "none", "i prefer not to answer"]:
        option['notApplicable'] = True
    return option

# Converts the data type in 'UserFormatType' to one supported in LFS
DATA_TO_LFS_TYPE = {
    'datetime': 'date',
    'date': 'date',
    'string': 'text',
    'string (single)': 'text',
    'string (multiple can be selected)': 'text',
    'boolean (true/false)': "boolean",
    'boolean': "boolean",
    'decimal': 'decimal',
    'decimal (single)': 'decimal',
    'integer': 'long',
    'integer (single)': 'long',
    'computed (decimal)': 'computed',
    'computed (integer)': 'computed',
    'time': 'time',
}
def convert_to_LFS_data_type(userFormat):
    result = DATA_TO_LFS_TYPE.get(userFormat.strip().lower(), 'text')
    return result

def clean_title(title):
    multiple_visits = " - Study Visits (# = "
    multiple_types = " ("
    result = title
    if multiple_visits in title:
        result = title[:title.index(multiple_visits)]
    if multiple_types in result:
        result = result[:result.index(multiple_types)]
    return result.strip()

def clean_name(name):
    if is_new_form:
        return re.sub(':|\(|\)|\[|\]|\s|\,|\.|\n', '', name.replace("/", "-"))
    else:
        return name.replace("/", "-")

def parse_count(title):
    multiple_visits = " - Study Visits (# = "
    result = 1
    if multiple_visits in title:
        visit_string = title[title.index(multiple_visits) + len(multiple_visits):]
        visit_string = visit_string[:visit_string.index(")")]
        if "many" in visit_string:
            result = -1
        else:
            visits = visit_string.split(",")
            result = len(visits)
    return result

def parse_description(title):
    multiple_visits = " - Study Visits (# = "
    result = ""
    if multiple_visits in title:
        visit_string = title[title.index(multiple_visits) + len(multiple_visits):]
        visit_string = visit_string[:visit_string.index(")")]
        result = "Visits " + visit_string
    return result

# Creates a JSON file that contains the tsv file as an lfs:Questionnaire
def csv_to_json(title):
    questionnaires = []
    questionnaire = {}
    main_questionnaire = {}
    section = {}
    subsection = {}

    with open(title + '.csv') as tsvfile:
        reader = csv.DictReader(tsvfile, dialect='excel')
        for row in reader:
            if row['Report Type']:
                if (main_questionnaire):
                    questionnaires.append(dict.copy(main_questionnaire))
                    main_questionnaire = {}
                if (len(questionnaire) > 0):
                    if len(subsection) > 0:
                        if len(section) > 0:
                            section[clean_name(subsection['label'])] = dict.copy(subsection)
                        else:
                            questionnaire[clean_name(subsection['label'])] = dict.copy(subsection)
                        subsection = {}
                    if len(section) > 0:
                        questionnaire[clean_name(section['label'])] = dict.copy(section)
                        section = {}
                    questionnaires.append(dict.copy(questionnaire))
                questionnaire = {}
                questionnaire['jcr:primaryType'] = 'lfs:Questionnaire'
                num_submisssions = parse_count(row['Report Type'])
                if num_submisssions != -1:
                    questionnaire['maxPerSubject'] = num_submisssions
                    description = parse_description(row['Report Type'])
                    if (len(description) > 0):
                        questionnaire['description'] = description
                questionnaire['title'] = clean_title(row['Report Type'])
                questionnaire['jcr:reference:requiredSubjectTypes'] = ["/SubjectTypes/Patient"]
            elif len(questionnaire) == 0:
                num_submisssions = parse_count(title)
                if num_submisssions != 1:
                    questionnaire['maxPerSubject'] = num_submisssions
                    description = parse_description(title)
                    if (len(description) > 0):
                        questionnaire['description'] = description
                questionnaire['jcr:primaryType'] = 'lfs:Questionnaire'
                questionnaire['title'] = clean_title(title)
                questionnaire['maxPerSubject'] = parse_count(title)
                questionnaire['jcr:reference:requiredSubjectTypes'] = ["/SubjectTypes/Patient"]
            if 'recurrent' in row and row['recurrent']:
                if len(subsection) > 0:
                    if len(section) > 0:
                        section[clean_name(subsection['label'])] = dict.copy(subsection)
                    else:
                        questionnaire[clean_name(subsection['label'])] = dict.copy(subsection)
                subsection = {}

                if row['recurrent'] != "end":
                    subsection['jcr:primaryType'] = 'lfs:Section'
                    subsection['label'] = clean_title(row['recurrent'])
                    subsection['recurrent'] = True

            if row['Sub-report']:
                if len(subsection) > 0:
                    if len(section) > 0:
                        section[clean_name(subsection['label'])] = dict.copy(subsection)
                    else:
                        questionnaire[clean_name(subsection['label'])] = dict.copy(subsection)
                subsection = {}
                if len(section) > 0:
                    questionnaire[clean_name(section['label'])] = dict.copy(section)
                section = {}

                num_submisssions = parse_count(row['Sub-report'])
                if (main_questionnaire):
                    questionnaires.append(dict.copy(questionnaire))
                    questionnaire = dict.copy(main_questionnaire)
                    main_questionnaire = {}
                parent_submission_limit = questionnaire['maxPerSubject'] if 'maxPerSubject' in questionnaire else -1
                if num_submisssions != 1 and num_submisssions != parent_submission_limit:
                    main_questionnaire = dict.copy(questionnaire)
                    questionnaire = {}
                    questionnaire['jcr:primaryType'] = 'lfs:Questionnaire'
                    if num_submisssions != -1:
                        questionnaire['maxPerSubject'] = num_submisssions
                        description = parse_description(row['Sub-report'])
                        if (len(description) > 0):
                            questionnaire['description'] = description
                    questionnaire['title'] = clean_title(row['Sub-report'])
                    questionnaire['jcr:reference:requiredSubjectTypes'] = ["/SubjectTypes/Patient"]
                section['jcr:primaryType'] = 'lfs:Section'
                section['label'] = clean_title(row['Sub-report'])

            parent = subsection if len(subsection) > 0 else (section if len(section) > 0 else questionnaire)

            question = clean_name(row['Content Header'].strip().lower())
            if question and 'Response Required?' in row and row['Response Required?'].lower().endswith("other"):
                # Skip this row as the previous list should have an "other" text field
                continue
            if question and question.endswith("_#"):
                question = question[:len(question) - 2]
            if question and row['Field Type']:
                text = row['Question'].strip() or question
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
                        'dataType': convert_to_LFS_data_type(row['Field Type'])
                    }
                else:
                    parent[question] = {
                        'jcr:primaryType': 'lfs:Question',
                        'text': text,
                        'dataType': convert_to_LFS_data_type(row['Field Type'])
                    }
                if row['Options (if applicable)']:
                    process_options(parent[question], row)
                    if not "multiple" in row['Field Type']:
                        parent[question]['maxAnswers'] = 1
                if 'Description' in row and row['Description'] != '':
                    parent[question]['description'] = row['Description']
                if 'Units' in row and row['Units'] != '':
                    parent[question]['unitOfMeasurement'] = row['Units']
                if 'Min Value' in row and row['Min Value']:
                    parent[question]['minValue'] = float(row['Min Value'])
                if 'Max Value' in row and row['Max Value']:
                    parent[question]['maxValue'] = float(row['Max Value'])
                if row['Field Type'].endswith("(single)"):
                    parent[question]['maxAnswers'] = 1
                if 'Max Answers' in row and row['Max Answers']:
                    parent[question]['maxAnswers'] = int(row['Max Answers'])
                if 'Compact' in row and row['Compact'] != '':
                    value = row['Compact']
                    if value[0].lower() == "y":
                        parent[question]['compact'] = True
                # Response Required should be the last conditional property.
                # Otherwise, parent[question] may error out if a conditional section has been created
                if 'Response Required?' in row and row['Response Required?']:
                    value = row['Response Required?']
                    if value[0].lower() == "y":
                        insert_min_answers(parent[question], row)
                    if len(value) > 4 and value[2:4].lower() == "if":
                        if len(subsection) > 0:
                            # Add the condition to the current subsection
                            prepare_conditional_string(value [5:], parent)
                        else:
                            # Add the condition to a new section wrapping the current question
                            previous_data = parent[question]
                            parent.update({question + 'Section': {
                                'jcr:primaryType': 'lfs:Section'
                            }})
                            parent[question + 'Section'][question] = previous_data
                            prepare_conditional_string(value [5:], parent[question + 'Section'])
                            # The presence of a conditional will also prevent the question from being inserted into the main thing
                            del parent[question]

    if len(subsection) > 0:
        if len(section) > 0:
            section[clean_name(subsection['label'])] = dict.copy(subsection)
        else:
            questionnaire[clean_name(subsection['label'])] = dict.copy(subsection)
    if len(section) > 0:
        questionnaire[clean_name(section['label'])] = dict.copy(section)
    questionnaires.append(dict.copy(questionnaire))
    if (main_questionnaire):
        questionnaires.append(dict.copy(main_questionnaire))

    for q in questionnaires:
        title = q['title'].replace(": ", " - ")
        with open(title + '.json', 'w') as jsonFile:
            json.dump(q, jsonFile, indent='\t')
        print('python3 cards/Utilities/JSON-to-XML/json_to_xml.py "' + title +'.json" > "' + title + '.xml";\\')



titles = [
    '6MWD', 'ActiGraph Data', 'Adverse events', 'Baseline Health Information', 'Enrollment Status',
    'Historical Stress Test', 'Historic Lab Results', 'Questionnaires', 'CPET Interpretation',
    'New Questionnaires'
]
for title in titles:
    if (title in ['New Questionnaires']):
        is_new_form = True
    csv_to_json(title)
