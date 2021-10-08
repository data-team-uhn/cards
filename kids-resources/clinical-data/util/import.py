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
    DATETIME = 10

SECTION_TYPES = [RowTypes.SECTION, RowTypes.SECTION_RECURRENT, RowTypes.SECTION_CONDITIONAL]
CONDITION_DEFINTIONS = ["if", "displayed if:", "show the field only if:", "show only if:", "show only if",
    "show the field only if", "show field only if", "show this field only if", "show this field if", "show field if",
    "yes if", "show if", "show the field only if:", "show field only if"]
CONDITION_SPLIT = [" = ", "selection was ", " was ", " selection is ", " response is ", " selections is ", " is "]
MULTIPLE_DEFINITIONS = ["select all that apply", "select all", "allow for multiple", "check all"]
UNIT_DEFINITIONS = ["in "]
OPTIONS_RANGE_DEFINITIONS = ["valid range:", "validrange:"]
MIN_RANGE_DEFINITIONS = ["min:", "min"]
MAX_RANGE_DEFINITIONS = ["max:", "max"]
TITLE_RANGE_DEFINITIONS = ["range:", "range"]
REQUIRED_DEFINITION = ["required field"]
SECTION_PREFIX = ["section: ", "tab : ", "tab: "]
CONDITIONAL_USE_PREVIOUS = "previous_list"
question_text_to_title_map = {}
question_title_list = []
section_title_list = []
incomplete_conditionals = []
previous_list_title = ""
section_index = 0

class Headers1:
    SECTION = "Section Name"
    QUESTION = "Field / Question"
    NAME = "Short name"
    DEFINITION = "Variable Values"
    CONDITIONS = "Further Instructions"
    CONDITION_QUESTION = True
    MERGED_DEFINITIONS_OPTIONS = False

class Headers1Impact(Headers1):
    SECTION = "Section Name (Rearranged Variables in Non-Bold)"

class Headers1NPC_QIC(Headers1):
    CONDITION_QUESTION = False
    USE_OPTION_CODES = True
    OPTION_CODES = "Variable Code"
    SPLIT_CONDITIONS_AT_COMMAS = True

class Headers2:
    SECTION = "Section Name"
    QUESTION = "Description"
    NAME = "Client Field"
    DEFINITION = "Type"
    OPTIONS = "Values"
    CONDITIONS = "Constraints"
    CONDITION_QUESTION = False
    MERGED_DEFINITIONS_OPTIONS = False

class Headers3:
    QUESTION = "Field/Question "
    DEFINITION = "Variable values"
    CONDITIONS = "Second order variable"
    CONDITION_QUESTION = True
    MERGED_DEFINITIONS_OPTIONS = True

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

def append_description(question, text):
    escaped_text = escape_description(text)
    question['description'] = question['description'] + ". " + escaped_text if "description" in question else escaped_text
    return question

def escape_description(text):
    # Escape any Markdown formating characters that are in the description as these are unintentional
    escaped_text = text.replace('*', '\\*').replace('`', '\\`')
    if (len(escaped_text) > 0 and text[0] in ['#', '>']):
        escaped_text = '\\' + escaped_text
    if (escaped_text.find("1.") == 0):
        escaped_text = '\\' + escaped_text
    return escaped_text

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
        # No title is needed because only a single cards:Conditional will be created
        insert_conditional(conditional_string, question, '')

# Updates the question with cards:Conditionals from the output of prepare_conditional
def insert_conditional(conditional_string, question, title):
    # Split the conditional into two operands and an operator
    conditional_string = partition_conditional_string(conditional_string)
    operand_a = conditional_string[0]
    operator = conditional_string[1]
    operand_b = conditional_string[2]
    # If the first operand is a comma-separated list, create a separate conditional for each
    # Enclose the conditionals in a cards:ConditionalGroup
    if ',' in operand_a:
        question.update({'conditionalGroup': {'jcr:primaryType': 'cards:ConditionalGroup'}})
        # The keyword 'all' in the conditional string should correspond to 'requireAll' == true
        # If it is present, remove it from the operand and add 'requireAll' to the conditional group
        if 'all' in operand_a:
            operand_a = operand_a[:-3]
            question['conditionalGroup'].update({'requireAll': True})
        operand_a_list = list(operand_a.split(','))
        for index, item in enumerate(operand_a_list):
            question['conditionalGroup'].update(create_conditional(item, operator, operand_b, 'condition' + str(index), question))
    # If the second operand is a comma-separated list, create a separate conditional for each
    # Enclose the conditionals in a cards:ConditionalGroup
    elif ',' in operand_b or ' or ' in operand_b:
        question.update({'conditionalGroup': {'jcr:primaryType': 'cards:ConditionalGroup'}})
        # The keyword 'all' in the conditional string should correspond to 'requireAll' == true
        # If it is present, remove it from the operand and add 'requireAll' to the conditional group
        if 'all' in operand_b:
            operand_b = operand_b[:-3]
            question['conditionalGroup'].update({'requireAll': True})
        operand_b_list = split_ignore_strings(operand_b, [", ", " or "])
        for index, item in enumerate(operand_b_list):
            question['conditionalGroup'].update(create_conditional(operand_a, operator, item, 'condition' + str(index), question))
    else:
        question.update(create_conditional(operand_a, operator, operand_b, 'condition' + title, question))

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
    match = regex.search('=|<>|<|>|>=|<=', conditional_string)
    if not match:
        # No operator detected, return everything as a single operand
        print("Failed to parse conditional: " + conditional_string)
        return conditional_string, '', ''

    seperator = match.group(0)
    parts = regex.split(seperator, conditional_string, 1)
    parts[0] = parts[0].strip()
    parts[1] = parts[1].strip()
    if seperator == "=" and parts[1].lower().startswith("not "):
        seperator = "<>"
        parts[1] = parts[1][4:]
    return parts[0], seperator, parts[1]

# Returns a dict object that is formatted as an cards:Conditional
def create_conditional(operand_a, operator, operand_b, title, question):
    operand_a_updated = operand_a.strip()
    result = {
        'jcr:primaryType': 'cards:Conditional'
    }
    if operand_a_updated == CONDITIONAL_USE_PREVIOUS:
        operand_a_updated = previous_list_title
    elif hasattr(Headers, "CONDITION_QUESTION") and Headers.CONDITION_QUESTION:
        if len(operand_a_updated) > 0 and operand_a_updated[0] == "\"" and operand_a_updated[-1] == "\"":
            operand_a_updated = operand_a_updated[1:-1]

        short_name = question_text_to_title_map.get(operand_a_updated.lower(), "")
        if short_name != "":
            operand_a_updated = short_name
        else:
            incomplete_conditionals.append({operand_a_updated.lower(): result})
            if operand_b == "":
                # print("Invalid operand_b for '{}' {} '{}'".format(operand_a_updated, operator, operand_b))
                pass
    if len(operand_a_updated) > 1 and operand_a_updated[0] == "[" and operand_a_updated[len(operand_a_updated) - 1] == "]":
        operand_a_updated = operand_a_updated[1:len(operand_a_updated) - 1]

    is_reference = False
    operand_b_updated = operand_b
    if operand_b_updated[0] == "\"" and operand_b_updated[len(operand_b_updated) - 1] == "\"":
        operand_b_updated = operand_b_updated[1:len(operand_b_updated) - 1]

    result.update({'operandA': {
        'jcr:primaryType': 'cards:ConditionalValue',
        'value': [operand_a_updated.lower()],
        'isReference': True
    }})
    result.update({'comparator': operator})
    result.update({'operandB': {
        'jcr:primaryType': 'cards:ConditionalValue',
        'value': [operand_b_updated],
        'isReference': is_reference
    }})

    # If the operator is <>, make sure that all entries for operand_a meet that requirement
    if (operator == "<>"):result['operandA']['requireAll'] = True
    return {title: result}

def get_row_type(row):
    row_type = RowTypes.DEFAULT
    if row[Headers.DEFINITION]:
        variable = row[Headers.DEFINITION].lower()
        if (("mm" in variable and ("yyyy" in variable or "yr" in variable)) or "date" in variable or "timestamp" in variable):
            row_type = RowTypes.DATE
        elif (("hh" in variable and "mm" in variable)):
            # Time must be checked after date, as datetimes should be parsed as dates
            row_type = RowTypes.TIME
        elif ("\n" in variable or "radio" in variable or "boolean" in variable or '[]' in variable or (hasattr(Headers, "OPTIONS") and Headers.OPTIONS in row and "\n" in row[Headers.OPTIONS])):
            row_type = RowTypes.LIST
        elif ("numeric" in variable or "integer" in variable or "smallint" in variable):
            row_type = RowTypes.NUMBER
        elif ("text field" == variable or "text" == variable or "varchar" in variable):
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
            # print("Unexpected Variable Values \"{}\" found for question \"{}\"".format(row[Headers.DEFINITION],row.get("NAME", "N/A")))
            pass
    elif row[Headers.QUESTION]:
        if (any(prefix in row[Headers.QUESTION].lower() for prefix in SECTION_PREFIX)):
            row_type = RowTypes.SECTION
        else:
            # print("No variable type determined for question \"{}\"".format(row[Headers.QUESTION]))
            pass
    else:
        if (row[Headers.NAME] and any(prefix in row[Headers.NAME].lower() for prefix in SECTION_PREFIX)):
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
    for prefix in SECTION_PREFIX:
        if prefix in title.lower():
            result = result[len(prefix):]
    return result.strip().replace('/','')

def clean_name(name):
    return re.sub(':|\(|\)|\[|\]| ', '', name.replace("/", "-"))

def row_starts_section(row, row_type):
    result = row_type in SECTION_TYPES or (hasattr(Headers, "SECTION") and Headers.SECTION in row and row[Headers.SECTION] and not row[Headers.SECTION].lower().startswith("see"))
    return result

def get_section_title(row):
    global section_index
    title = ""
    if (hasattr(Headers, "SECTION") and Headers.SECTION in row and row[Headers.SECTION]):
        title = row[Headers.SECTION]
    elif (hasattr(Headers, "NAME") and row[Headers.NAME]):
        title = row[Headers.NAME]
    elif (row[Headers.QUESTION]):
        title = row[Headers.QUESTION]
    elif (any(prefix in row[Headers.QUESTION].lower() for prefix in SECTION_PREFIX)):
        title = row[Headers.QUESTION]
    else:
        section_index += 1
        title = "Section{}".format(section_index)
    return title

def start_questionnaire(title):
    questionnaire = {}
    questionnaire['jcr:primaryType'] = 'cards:Questionnaire'
    questionnaire['title'] = clean_title(title)
    questionnaire['jcr:reference:requiredSubjectTypes'] = ["/SubjectTypes/Patient"]
    return questionnaire

def start_section(questionnaire, section, row):
    section = end_section(questionnaire, section)
    section['jcr:primaryType'] = 'cards:Section'
    section['label'] = clean_title(get_section_title(row))
    return section

def end_section(questionnaire, section):
    global section_title_list

    if len(section) > 0:
        title = clean_name(section['label'])
        if title in section_title_list:
            tag = 2
            while title + str(tag) in section_title_list:
                tag += 1
            title = title + str(tag)

        section_title_list.append(title)
        questionnaire[title] = dict.copy(section)
    return {}

def insert_recurrent(parent, row):
    parent['recurrent'] = True

def insert_date(question, row):
    date = row[Headers.DEFINITION]
    date = date.replace("D", "d").replace("Y", "y").replace("-", "/")
    question['dateFormat'] = date
    question['dataType'] = "date"

def process_conditions(parent, condition_text, question_title):
    if '\n' in condition_text and not ":\n" in condition_text and not parent[question_title]["dataType"] == "computed":
        separated = split_ignore_strings(condition_text, ["\n"], 1)
        if len(separated) > 1:
            parent[question_title] = append_description(parent[question_title], separated[1])
            condition_text = separated[0]
    conditions = [condition_text]
    if (";" in condition_text):
        conditions = re.split(';\s*', condition_text)
    # TODO: How to handle NPC descriptions
    # elif (hasattr(Headers, "SPLIT_CONDITIONS_AT_COMMAS") and Headers.SPLIT_CONDITIONS_AT_COMMAS == True and "," in condition_text):
        # conditions = re.split(',\s*', condition_text)
        # print(condition_text)
    for condition in conditions:
        parent = process_split_conditions(parent, condition, question_title)
    return parent

def process_split_conditions(parent, condition, question_title):
    new_question = {}
    lower = condition.lower()
    if question_title+"Section" in parent:
        parent[question_title+"Section"] = process_split_conditions(parent[question_title+"Section"], condition, question_title)
        return parent
    if "return" in lower:
        parent[question_title]['expression'] = condition
        return parent
    for starter in CONDITION_DEFINTIONS:
        if lower.startswith(starter):
            stripped_condition = condition[len(starter):].strip()
            for splitter in CONDITION_SPLIT:
                if splitter in stripped_condition:
                    stripped_condition = stripped_condition.replace(splitter, " = ")
            if any (separator in stripped_condition for separator in ["=", "<", ">", "<=", ">=", "<>"]) :
                if not new_question:
                    new_question = {
                        'jcr:primaryType': 'cards:Section',
                        question_title: parent[question_title]
                    }
                prepare_conditional_string(stripped_condition, new_question)
                parent.pop(question_title, None)
                parent[question_title + "Section"] = new_question
                return parent
    for starter in MULTIPLE_DEFINITIONS:
        if starter in lower:
            parent[question_title].pop('maxAnswers', None)
            # TODO: Handle starter + condition
            return parent
    for starter in UNIT_DEFINITIONS:
        if lower.startswith(starter):
            stripped_condition = lower[len(starter):].strip()
            parent[question_title]["Units"] = stripped_condition
            return parent
    # TODO: Deduplicate range code
    for starter in OPTIONS_RANGE_DEFINITIONS:
        if lower.startswith(starter):
            stripped_condition = lower[len(starter):].strip()
            value_range = re.split('-',stripped_condition)
            parent[question_title]["minValue"] = float(value_range[0]) if '.' in value_range[0] else int(value_range[0])
            parent[question_title]["maxValue"] = float(value_range[1]) if '.' in value_range[1] else int(value_range[1])
            parent[question_title]["dataType"] = "decimal"
            return parent
    for starter in MIN_RANGE_DEFINITIONS:
        if lower.startswith(starter) and lower.count(" ") == 1:
            return add_range_property(parent, question_title, starter, lower, True)
    for starter in MAX_RANGE_DEFINITIONS:
        if lower.startswith(starter) and lower.count(" ") == 1:
            return add_range_property(parent, question_title, starter, lower, False)
    for starter in REQUIRED_DEFINITION:
        if lower.startswith(starter):
            parent[question_title]["minAnswers"] = 1
            return parent
    parent[question_title] = append_description(parent[question_title], condition)
    return parent

def add_range_property(parent, question_title, starter, lower, is_minimum):
    stripped_condition = lower[len(starter) + 1:].strip()
    if (stripped_condition.count("-") == 2 and len(stripped_condition) == 10):
        # Time in format "YYYY-mm-dd"
        parent[question_title]["lowerLimit" if is_minimum else "upperLimit"] = stripped_condition
    elif (stripped_condition.count(":") == 1 and len(stripped_condition) == 5 and stripped_condition.find(":") == 2):
        # Time in format "HH:MM"
        parent[question_title]["lowerLimit" if is_minimum else "upperLimit"] = stripped_condition
    else:
        parent[question_title]["minValue" if is_minimum else "maxValue"] = float(stripped_condition) if '.' in stripped_condition else int(stripped_condition)
    return parent

def split_text(text):
    working_text = text
    results = []
    while len(working_text) > 2:
        dividers = []
        if working_text[len(working_text) - 1] == "]" and " [" in working_text:
            dividers = [" [", "]"]
        elif working_text[len(working_text) - 1] == ")" and " (" in working_text:
            dividers = [" (", ")"]
        else:
            break
        split = working_text[working_text.rindex(dividers[0]) + 2 : len(working_text) - 1]
        results.insert(0, split)

        working_text = working_text[:working_text.rindex(dividers[0])].strip()
    results.insert(0, working_text)
    return results


def insert_question(parent, row, question, row_type):
    text = row[Headers.QUESTION].strip() or question
    # Divide the questions' title into the main title and any comments
    divided = split_text(text)
    # Insert the main title
    parent[question] = {
        'jcr:primaryType': 'cards:Question',
        'text': divided[0],
        'maxAnswers': 1
    }

    # Determine what to do with any parsed comments
    if len(divided) > 1:
        for split in divided[1:]:
            is_range = False
            # Parse out the comment into a range if possible
            for starter in TITLE_RANGE_DEFINITIONS:
                if split.lower().startswith(starter):
                    stripped_condition = split[len(starter):].strip()
                    value_range = re.split('- |– ',stripped_condition)
                    parent[question]["minValue"] = float(value_range[0]) if '.' in value_range[0] else int(value_range[0])
                    parent[question]["maxValue"] = float(value_range[1]) if '.' in value_range[1] else int(value_range[1])
                    parent[question]["dataType"] = "decimal"
                    is_range = True
                    break
            if not is_range:
                # Detect if the comment is a unit. Unit criteria:
                # - (Optionally) starts with "in "
                # - No spaces in description
                # - Not a letter only string in all caps (eg. an acronym)
                # - Is less than 8 characters long OR contains a '/'
                #     Excludes long words, include complex units like 'kCal/kg/day'
                #     Common units like "Minutes", "Seconds" and "Celsius" are 7 letters long
                # - Is not "specify"
                conditional_split = split
                if conditional_split.find("in ") == 0 and conditional_split[3:].find(' ') == -1:
                    conditional_split = conditional_split[3:]
                if (conditional_split.find(' ') == -1
                    and not (conditional_split.isalpha() and conditional_split.isupper())
                    and not (len(conditional_split) > 7 and conditional_split.find('/') == -1)
                    and not conditional_split.lower() in ["specify"]):
                    parent[question]['unitOfMeasurement'] = conditional_split
                else:
                    # Otherwise, add the comment as a description
                    parent[question] = append_description(parent[question], split)

    if hasattr(Headers, "SECTION") and Headers.SECTION in row and row[Headers.SECTION] and row[Headers.SECTION].lower().startswith("see"):
        append_description(parent[question], row[Headers.SECTION])

    if(hasattr(Headers, "CONDITION_QUESTION") and Headers.CONDITION_QUESTION):
        global incomplete_conditionals
        question_original_name = row[Headers.QUESTION].lower()
        new_incomplete = []
        matching_incomplete = []
        for incomplete in incomplete_conditionals:
            if list(incomplete.keys())[0] == question_original_name:
                matching_incomplete.append(incomplete)
            else:
                new_incomplete.append(incomplete)
        incomplete_conditionals = new_incomplete
        for match in matching_incomplete:
            match[question_original_name]['operandA']['value'] = [question]

    question_text_to_title_map[text.lower()] = question
    question_title_list.append(question)
    insert_question_type(row, parent[question], row_type)

def insert_question_type(row, question, row_type):
    new_type = ""
    if row_type == RowTypes.DATE:
        new_type = "date"
        question['dateFormat'] = row[Headers.OPTIONS] if hasattr(Headers, "OPTIONS") and row[Headers.OPTIONS] else row[Headers.DEFINITION]
    elif row_type == RowTypes.TIME:
        new_type = "time"
    elif row_type == RowTypes.NUMBER:
        new_type = "decimal"
    elif row_type == RowTypes.TEXT:
        new_type = "text"
    elif row_type == RowTypes.LIST:
        new_type = "text"
        insert_list(row, question)
    elif row_type == RowTypes.CALCULATED:
        new_type = "computed"
        if hasattr(Headers, "CONDITIONS") and row[Headers.CONDITIONS]:
            question['expression'] = row[Headers.CONDITIONS]
    elif row_type == RowTypes.DEFAULT:
        new_type = "text"
        if len(row[Headers.DEFINITION]) > 0:
            question = append_description(question, row[Headers.DEFINITION])


    if not "dataType" in question:
        question['dataType'] = new_type

def insert_list(row, question):
    option_list = options_list(row[Headers.OPTIONS if hasattr(Headers, "OPTIONS") else Headers.DEFINITION])
    option_codes = options_list(row[Headers.OPTION_CODES]) if hasattr(Headers, "USE_OPTION_CODES") and Headers.USE_OPTION_CODES == True else []
    question.update({
        'displayMode': 'list'
    })
    for idx, option in enumerate(option_list):
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
                'jcr:primaryType': 'cards:AnswerOption',
                'label': label,
                'value': options[0].strip() if len(option_list) != len(option_codes) else option_codes[idx]
            }
            answer_option = {clean_name(options[0].strip()):
                add_option_properties(option_details, label)
            }
            question.update(answer_option)
        else:
            option_details = {
                'jcr:primaryType': 'cards:AnswerOption',
                'label': value.strip(),
                'value': value.strip() if len(option_list) != len(option_codes) else option_codes[idx]
            }
            answer_option = {clean_name(option.strip()):
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

# Creates a JSON file that contains the tsv file as an cards:Questionnaire
def csv_to_json(title):
    # Reset questionairre specific globals
    global incomplete_conditionals
    global question_text_to_title_map
    global question_title_list
    global section_title_list
    global previous_list_title
    global section_index
    incomplete_conditionals = []
    question_text_to_title_map = {}
    question_title_list = []
    section_title_list = []
    section_index = 0

    questionnaire = start_questionnaire(title)
    section = {}

    with open(title + '.csv', encoding="utf-8-sig") as csvfile:
        reader = csv.DictReader(csvfile, dialect='excel')
        for row in reader:
            row_type = get_row_type(row)
            if (row_starts_section(row, row_type)):
                section = start_section(questionnaire, section, row)
                if (row_type == RowTypes.SECTION_CONDITIONAL or (hasattr(Headers, "CONDITIONS") and row[Headers.CONDITIONS] and any(prefix in row[Headers.CONDITIONS].lower() for prefix in CONDITION_DEFINTIONS))):
                    condition = row[Headers.CONDITIONS]
                    lower = condition.lower()
                    for starter in CONDITION_DEFINTIONS:
                        if lower.startswith(starter):
                            stripped_condition = condition[len(starter):].strip()
                            for splitter in CONDITION_SPLIT:
                                if splitter in stripped_condition:
                                    stripped_condition = stripped_condition.replace(splitter, " = ")
                            if any (separator in stripped_condition for separator in ["=", "<", ">", "<=", ">=", "<>"]) :
                                prepare_conditional_string(stripped_condition, section)
                    if (row_type == RowTypes.SECTION_CONDITIONAL):
                        continue
                elif (row_type == RowTypes.SECTION_RECURRENT):
                    insert_recurrent(section, row)
                    continue
                elif (row_type == RowTypes.SECTION):
                    continue
            parent = section if len(section) > 0 else questionnaire
            question_title = clean_name(row[Headers.NAME if hasattr(Headers, "NAME") else Headers.QUESTION].strip().lower())
            # Optional debugging for skipped questions
            if (len(question_title) == 0):
                # print("Skipped row missing question title {}".format(row))
                continue
            elif ("\n" in question_title):
                # print("Skipped row with newline in name {}".format(question_title))
                continue
            # Skip this row since it occurs when a list input had "other" selected,
            # as the "other" text field in that list will replace this question.
            if question_title and Headers.CONDITIONS in row and row[Headers.CONDITIONS].lower().endswith("other"):
                continue
            # Convert any duplicate IDs into sequential IDs
            if question_title in question_title_list:
                tag = 2
                while question_title + str(tag) in question_title_list:
                    tag += 1
                question_title = question_title + str(tag)
            insert_question(parent, row, question_title, row_type)
            if question_title and question_title.endswith("_#"):
                question_title = question_title[:len(question_title) - 2]


            # TODO: Add to Headers?
            # Not used for new imports, kept in for compatibility with cardiac_rehab
            if 'Units' in row and row['Units'] != '':
                parent[question_title]['unitOfMeasurement'] = row['Units']
            if 'Min Value' in row and row['Min Value']:
                parent[question_title]['minValue'] = float(row['Min Value'])
            if 'Max Value' in row and row['Max Value']:
                parent[question_title]['maxValue'] = float(row['Max Value'])
            if row[Headers.QUESTION].endswith("(single)"):
                parent[question_title]['maxAnswers'] = 1
            if 'Compact' in row and row['Compact'] != '':
                value = row['Compact']
                if value[0].lower() == "y":
                    parent[question_title]['compact'] = True
            # End unused section

            # Response Required should be the last conditional property.
            # Otherwise, parent[question_title] may error out if a conditional section has been created
            if Headers.CONDITIONS in row and row[Headers.CONDITIONS]:
                conditional = row[Headers.CONDITIONS]
                if conditional[0].lower() == "y":
                    insert_min_answers(parent[question_title], row)
                parent = process_conditions(parent, conditional, question_title)

            if row_type == RowTypes.LIST:
                previous_list_title = question_title

    end_section(questionnaire, section)

    # for q in questionnaires:
    with open(title + '.json', 'w') as jsonFile:
        json.dump(questionnaire, jsonFile, indent='\t')
    print('python3 cards/Utilities/JSON-to-XML/json_to_xml.py "' + title +'.json" > "' + title + '.xml";\\')

# Specify the titles of each csv file and which set of column titles and options should be used
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
    ['ACTION', Headers3]
]
Headers = Headers1
for title in titles:
    Headers = title[1]
    csv_to_json(title[0])
