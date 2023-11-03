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

from optparse import Option
import argparse
import os
import enum
import json
import csv
import re



#===============
# Configurations
#===============

# Supported values for the data type column
class RowTypes(enum.Enum):
    DEFAULT = 0
    DATE = 1
    TEXT = 2
    BOOLEAN = 3
    DECIMAL = 4
    LONG = 5
    # COMPUTED = 6
    TIME = 7
    FILE = 8
    INFO_BOX = 9
    SECTION_START = 10
    SECTION_END = 11
    SECTION_RECURRENT = 12
    MATRIX_START = 13
    MATRIX_END = 14
    VOCABULARY = 15
    SECTION_REPEATED = 16

# Basic logging support
class Logging:
    RUN = 0
    ERROR = 1
    WARNING = 2
    INFO = 3

def log(log_level, object):
    if log_level <= Options.logging:
        print(object)

SECTION_TYPES = [RowTypes.SECTION_START, RowTypes.SECTION_RECURRENT, RowTypes.SECTION_END, RowTypes.SECTION_REPEATED]
MATRIX_TYPES = [RowTypes.MATRIX_START, RowTypes.MATRIX_END]
STRING_GROUPS = {
    "(": ")",
    "[": "]",
    "{": "}",
    "\"": "\"",
}

CONDITION_SPLIT = ["="]



#=======================
# Question Type handling
#=======================

# Provides support for applying any properties needed based on the specified question type
class RowTypeMap:
    def default_type_handler(self, questionnaire, row):
        log(Logging.INFO, "Setting data type " + self.output_type)
        questionnaire.question["dataType"] = self.output_type

    def __init__(self, row_type, match_string, simple_match=True, output_type="", handler=default_type_handler):
        self.row_type = row_type
        self.match_string = match_string
        self.simple_match = simple_match
        self.output_type = output_type if len(output_type) > 0 else match_string
        self.handler = handler

def section_start_handler(self, questionnaire, row):
    if (not Headers["SECTION"].has_value(row)):
        # Section will not have been created yet: Create a section with a unique name
        questionnaire.section_index += 1
        label = "Section {}".format(questionnaire.section_index)

        # Do not automatically finish the previous section as section rows support nested sections
        questionnaire.push_section(create_new_section(label, False))
    return

def recurrent_section_handler(self, questionnaire, row):
    section_start_handler(self, questionnaire, row)
    questionnaire.parent['recurrent'] = True

def repeated_section_handler(self, questionnaire, row):
    section_start_handler(self, questionnaire, row)
    questionnaire.parent['repeated'] = True
    parent_label = questionnaire.parent['title' if 'title' in questionnaire.parent else 'label']
    referenced_question_key = Headers['ENTRY_MODE_QUESTION'].get_value(row)
    referenced_question = questionnaire.parents[-2][referenced_question_key]

    for key in referenced_question.keys():
        entry = referenced_question[key]
        if type(entry) == dict and 'jcr:primaryType' in entry and entry['jcr:primaryType'] == 'cards:AnswerOption':
            conditional_label = parent_label + "_" + clean_name(entry['value'])
            new_section = create_new_section(conditional_label)
            new_section['label'] = entry['label']
            new_section['repeated_parent'] = parent_label
            questionnaire.push_section(new_section)
            condition_handle_brackets(questionnaire, new_section, referenced_question_key + "=\"" + entry['value'] + "\"")
            questionnaire.complete_section()

def end_repeated_section(self, questionnaire, row):
    parent_label = questionnaire.parent['title' if 'title' in questionnaire.parent else 'label']

    repeated_conditionals = []
    non_repeated_children = []

    for key in questionnaire.parent.keys():
        entry = questionnaire.parent[key]
        if type(entry) == dict:
            if 'repeated_parent' in entry and entry['repeated_parent'] == parent_label:
                entry.pop('repeated_parent')
                repeated_conditionals.append(key)
            else:
                non_repeated_children.append(key)

    for non_repeated_key in non_repeated_children:
        non_repeated_child = questionnaire.parent.pop(non_repeated_key)
        for repeated_key in repeated_conditionals:
            questionnaire.parent[repeated_key][repeated_key + "_" + non_repeated_key] = non_repeated_child

    questionnaire.parent.pop('repeated')

def section_end_handler(self, questionnaire, row):
    if is_section(questionnaire.parent):
        if 'repeated' in questionnaire.parent:
            end_repeated_section(self, questionnaire, row)

    if is_matrix(questionnaire.parent):
        # End any matrix sections first as matrixes cannot span section borders
        questionnaire.complete_section()

    # End a normal section
    questionnaire.complete_section()

# Set up a new matrix section
def matrix_start_handler(self, questionnaire, row):
    # No nested matrixes: Complete the previous matrix if present
    matrix_end_handler(self, questionnaire, row)

    # Set the new section to be a matrix section
    questionnaire.parent['displayMode'] = 'matrix'

    if (Options.max_answers > 0):
        questionnaire.parent['maxAnswers'] = 1

    # Matrixes are defined with question type = "matrix start <matrix type>"
    # Parse out and apply <matrix type>
    type_string = remove_current_type(self, row)

    type_map = get_row_type_map_from_string(type_string)
    type_map.handler(type_map, questionnaire, row)

def matrix_end_handler(self, questionnaire, row):
    if is_matrix(questionnaire.parent):
        questionnaire.complete_section()

def datetime_handler(self, questionnaire, row):
    # Create the date or time question normally
    RowTypeMap.default_type_handler(self, questionnaire, row)

    # Dates / Times are defined with question type = "matrix start <matrix type>"
    # Parse out and apply <matrix type>
    date_format = remove_current_type(self, row)

    # Assume the remaining string is a well formatted date or time string.
    # TODO: Can this be verified in some way?
    if (len(date_format) > 0):
        questionnaire['dateFormat'] = date_format

def info_handler(self, questionnaire, row):
    RowTypeMap.default_type_handler(self, questionnaire, row)
    questionnaire.question["jcr:primaryType"] = "cards:Information"

# For rows with multiple different types in the question type field,
# return a string that skips the first type
def remove_current_type(self, row):
    type_string = Headers["TYPE"].get_value(row)
    # Remove the matrix start type definition
    type_string = type_string[len(self.match_string):].strip()
    return type_string

RowTypesMappings = [
    RowTypeMap(RowTypes.MATRIX_START, "matrix start", False, "", matrix_start_handler),
    RowTypeMap(RowTypes.DATE, "date", False, "", datetime_handler),
    RowTypeMap(RowTypes.TIME, "time", False, "", datetime_handler),
    RowTypeMap(RowTypes.VOCABULARY, "vocabulary", False), # TODO: Handle sourceVocabularies
    RowTypeMap(RowTypes.TEXT, "text"),
    RowTypeMap(RowTypes.BOOLEAN, "boolean"), # TODO: Anything more needed here?
    RowTypeMap(RowTypes.DECIMAL, "decimal"),
    RowTypeMap(RowTypes.LONG, "long"),
    RowTypeMap(RowTypes.FILE, "file"),
    RowTypeMap(RowTypes.INFO_BOX, "info box", True, "info", info_handler),
    RowTypeMap(RowTypes.SECTION_START, "section start", True, "", section_start_handler),
    RowTypeMap(RowTypes.SECTION_END, "section end", True, "", section_end_handler),
    RowTypeMap(RowTypes.SECTION_RECURRENT, "recurrent section", True, "", recurrent_section_handler),
    RowTypeMap(RowTypes.MATRIX_END, "matrix end", True, "", matrix_end_handler),
    RowTypeMap(RowTypes.SECTION_REPEATED, "repeated section", True, "", repeated_section_handler)
]
DefaultRowTypeMap = RowTypeMap(RowTypes.DEFAULT, "", True, "text")



#===============
# State Handling
#===============

# An class used to store the current state of questionnaires being built.
# Includes references to the questionnaire, section and question that is currently being created
# as well as other data that is required between multiple rows and/or columns
class QuestionnaireState:
    def __init__(self):
        self.clear_state()

    def clear_state(self):
        # List of all questionnaires defined in the current CSV
        self.questionnaires = []
        self.clear_questionnaire()

    def clear_questionnaire(self):
        # How many unnamed sections have been created, used to create a unique generic section title
        self.section_index = 0
        # List of all used section or question titles
        self.section_title_list = []
        self.question_title_list = []
        # A list of any conditionals that do not have a valid link to a question
        self.incomplete_conditionals = []


        # Currently being created objects
        self.questionnaire = {}
        self.parent = {}
        self.question = {}
        # Stack of currently being created questionnaires/sections
        self.parents = []

    # Add a new questionnaire to the list of questionnaires and update current regerences
    def add_questionnaire(self, questionnaire):
        self.complete_questionnaire()
        self.questionnaire = questionnaire
        self.questionnaires.append(questionnaire)
        self.parents = [questionnaire]
        self.parent = questionnaire
        self.question = questionnaire

    # Finish off the current questionnaire and any children
    def complete_questionnaire(self):
        self.complete_question()
        while len(self.parents) > 1:
            self.complete_section()

    # Push a new section into the current questionnaire/section stack
    def push_section(self, section, complete_question=True):
        if complete_question:
            self.complete_question()
        self.parents.append(section)
        self.parent = section
        if complete_question:
            self.question = section

    # Complete the current section and remove it from the currently active questionnaire/section stack.
    # Adds the current section as a child node of the object above it in the stack.
    def complete_section(self):
        if (is_section(self.parent)):
            log(Logging.INFO, "Completing section")
            self.complete_question()
            section = self.parents.pop()

            # Sections defined with a section text and the start section row type may not have a title
            title = clean_name(section.pop('title') if 'title' in section else section['label'])
            if title in self.section_title_list:
                tag = 2
                while title + " " + str(tag) in self.section_title_list:
                    tag += 1
                title = title + " " + str(tag)

            self.section_title_list.append(title)
            self.parents[-1][title] = section
            self.parent = self.parents[-1]
            self.question = self.parent
            return True
        else:
            return False

    # Add a question to the current active questionnaire/section
    def add_question(self, name, question):
        self.complete_question()
        self.question = question
        question['name'] = name

    def complete_question(self):
        if is_question(self.question):
            name = self.question.pop('name')
            self.parent[name] = self.question
            self.question = self.parent



#=====================
# Column Data Handlers
#=====================
class HeaderColumn:
    def default_column_handler(self, questionnaire, row):
        questionnaire.question[self.name] = self.get_value(row)

    def __init__(self, column, name, handler=default_column_handler):
        self.column = column
        self.name = name
        self.handler = handler

    def has_value(self, row):
        result = self.column in row and row[self.column]
        return result

    def get_value(self, row):
        return row[self.column]

def questionnaire_handler(self, questionnaire, row):
    new_questionnaire = create_new_questionnaire(self.get_value(row))
    questionnaire.add_questionnaire(new_questionnaire)

def condition_handler(self, questionnaire, row):
    process_conditional(self, questionnaire, row)
    return

def section_handler(self, questionnaire, row):
    label = self.get_value(row)

    if (get_row_type_map(row).row_type not in (SECTION_TYPES + MATRIX_TYPES)):
        # This section is defined in line with a question.
        # For this simpler section definition style, complete the previous section automatically
        questionnaire.complete_section()

    questionnaire.push_section(create_new_section(label))

def question_handler(self, questionnaire, row):
    title = self.get_value(row).strip().lower()
    if get_row_type_map(row).row_type in (SECTION_TYPES + MATRIX_TYPES):
        if Headers["SECTION"].has_value(row):
            questionnaire.parent["title"] = title
        else:
            questionnaire.push_section(create_new_section(""))
    else:
        create_question(questionnaire, self.get_value(row).strip().lower())

def title_handler(self, questionnaire, row):
    if not Headers["QUESTION"].has_value(row):
        create_question(questionnaire, self.get_value(row).strip().lower())

    self.default_column_handler(questionnaire, row)
    return

def create_question(questionnaire, name):
    log(Logging.INFO, "Creating question " + name)
    question_title = clean_name(name)

    # Convert any duplicate IDs into sequential IDs
    if question_title in questionnaire.question_title_list:
        tag = 2
        while question_title + str(tag) in questionnaire.question_title_list:
            tag += 1
        question_title = question_title + str(tag)

    question = {
        'jcr:primaryType': 'cards:Question',
        'name': question_title,
        'maxAnswers': 1
    }
    questionnaire.add_question(question_title, question)
    questionnaire.question_title_list.append(question)


def type_handler(self, questionnaire, row):
    type_map = get_row_type_map(row)
    type_map.handler(type_map, questionnaire, row)

def compact_handler(self, questionnaire, row):
    questionnaire.question[self.name] = self.get_value(row) == 'Y'

def option_handler(self, questionnaire, row):
    insert_list(self.get_value(row), questionnaire.question)

def number_handler(self, questionnaire, row):
    value = self.get_value(row)
    questionnaire.question[self.name] = float(value) if '.' in value else int(value)

def min_value_handler(self, questionnaire, row):
    range_value_handler(self, questionnaire, row, "lowerLimit")

def max_value_handler(self, questionnaire, row):
    range_value_handler(self, questionnaire, row, "upperLimit")

def range_value_handler(self, questionnaire, row, datetime_limit):
    rowType = get_row_type_map(row).row_type
    if rowType == RowTypes.DATE or rowType == RowTypes.TIME:
        questionnaire.question[datetime_limit] = self.get_value(row)
    else:
        number_handler(self, questionnaire, row)



# Create a dictionary containing all the columns expected in a form spreadsheet
# This dictionary maps columns to expected json property names and a handler
# that should be used to interpret the column's values
DefaultHeaders = {}
DefaultHeaders["QUESTIONNAIRE"] = HeaderColumn("Questionnaire Name", "", questionnaire_handler)
DefaultHeaders["SECTION"] = HeaderColumn("Section Name", "", section_handler)
DefaultHeaders["QUESTION"] = HeaderColumn("Variable Name", "", question_handler)
DefaultHeaders["TYPE"] = HeaderColumn("Question Type", "", type_handler)
DefaultHeaders["TITLE"] = HeaderColumn("Question Text", "text", title_handler)
DefaultHeaders["DESCRIPTION"] = HeaderColumn("Description", "description")
DefaultHeaders["OPTIONS"] = HeaderColumn("Options", "", option_handler)
DefaultHeaders["CONDITIONS"] = HeaderColumn("Conditional Display", "", condition_handler)
DefaultHeaders["EXPRESSION"] = HeaderColumn("Specify Calculation", "expression")
DefaultHeaders["COMPACT"] = HeaderColumn("Compact", "compact", compact_handler)
DefaultHeaders["MIN_ANSWERS"] = HeaderColumn("Min Answers", "minAnswers", number_handler)
DefaultHeaders["MAX_ANSWERS"] = HeaderColumn("Max Answers", "maxAnswers", number_handler)
DefaultHeaders["UNITS"] = HeaderColumn("Units", "unitOfMeasurement")
DefaultHeaders["MIN_VALUE"] = HeaderColumn("Min Value", "minValue", min_value_handler)
DefaultHeaders["MIN_VALUE_LABEL"] = HeaderColumn("Min Value Label", "minValueLabel")
DefaultHeaders["MAX_VALUE"] = HeaderColumn("Max Value", "maxValue", max_value_handler)
DefaultHeaders["MAX_VALUE_LABEL"] = HeaderColumn("Max Value Label", "maxValueLabel")
DefaultHeaders["DISPLAY"] = HeaderColumn("Display Mode", "displayMode")
DefaultHeaders["SLIDER_STEP"] = HeaderColumn("Slider Step", "sliderStep", number_handler)
DefaultHeaders["SLIDER_MARK"] = HeaderColumn("Slider Mark Step", "sliderMarkStep", number_handler)
DefaultHeaders["SLIDER_ORIENTATION"] = HeaderColumn("Slider Orientation", "sliderOrientation")
DefaultHeaders["ENTRY_MODE"] = HeaderColumn("Entry Mode", "entryMode")
DefaultHeaders["ENTRY_MODE_QUESTION"] = HeaderColumn("Reference Question", "question")



#==================
# Utility functions
#==================

# Take an input string and split it into two at the first instance of splitter.
# If splitter is not present, return the input string
# Inputs: input (string), splitter (string)
def partition_ignore_strings(input, splitter):
    return split_ignore_strings(input, [splitter], 1)

# Take an input string and split it at any instances of a string in splitters.
# Limit controls the maximum amount of splits that can occur. -1 means no limit
# Inputs: input (string), splitters (array of strings), limit (integer)
def split_ignore_strings(input, splitters, limit = -1):
    ignore_list = []
    results = []
    number_splits = 0
    i = 0
    last_split = 0
    while i < len(input) and (limit == -1 or number_splits <= limit):
        if len(ignore_list) > 0 and input[i] == ignore_list[len(ignore_list) - 1]:
            ignore_list.pop()
        elif input[i] in STRING_GROUPS.keys():
            ignore_list.append(STRING_GROUPS[input[i]])
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
        print("Split ignore list not cleared for '{}': {}".format(input, ignore_list))
        pass
    return results

# Determine if an object is a question based on primary type
def is_question(parent):
    return 'jcr:primaryType' in parent and ('cards:Question' == parent['jcr:primaryType'] or 'cards:Information' == parent['jcr:primaryType'])

# Determine if an object is a section based on primary type
def is_section(parent):
    return 'cards:Section' == parent['jcr:primaryType']

# Determine if an object is a matrix based on display mode
def is_matrix(parent):
    return is_section(parent) and 'displayMode' in parent and 'matrix' == parent['displayMode']

# Clean a title for display to the user
def clean_title(title):
    result = title
    return result.strip().replace('/','')

# Clean a string for use in a node name
# TODO: replace with white list
def clean_name(name):
    result = re.sub(':|\(|\)|\[|\]| |,', '', name.replace("/", "-"))
    return result[:40]

# Create a questionnaire object containing a title and all the default quetionnaire properties
def create_new_questionnaire(title):
    new_questionnaire = {}
    new_questionnaire['jcr:primaryType'] = 'cards:Questionnaire'
    new_questionnaire['title'] = clean_title(title)
    new_questionnaire['jcr:reference:requiredSubjectTypes'] = [Options.subject_types]
    new_questionnaire['paginate'] = Options.paginate
    if Options.max_per_subject >  0:
        new_questionnaire['maxPerSubject'] = Options.max_per_subject
    return new_questionnaire

# Create a questionnaire object containing a title and all the default questionnaire properties
def create_new_section(title, title_as_label=True):
    log(Logging.INFO, "Creating section {}".format(title))
    new_section = {}
    new_section['jcr:primaryType'] = 'cards:Section'
    if(len(title) > 0):
        new_section['label' if title_as_label else 'title'] = clean_title(title)
    return new_section

# Determine what question type a row should be
def get_row_type_map(row):
    row_type = DefaultRowTypeMap
    if Headers["TYPE"].has_value(row):
        type_string = Headers["TYPE"].get_value(row)
        row_type = get_row_type_map_from_string(type_string)
    else:
        log(Logging.WARNING, "Skipped row {}".format(row))
        pass
    return row_type

def get_row_type_map_from_string(type_string):
    row_type = DefaultRowTypeMap

    type_string = type_string.lower()
    for mapping in RowTypesMappings:
        if not mapping.simple_match:
            # Complex row type so mapping may not be a direct equals
            if type_string.startswith(mapping.match_string):
                row_type = mapping
                break
        elif type_string == mapping.match_string:
            row_type = mapping
            break
    return row_type

# Insert a list of options into a question
def insert_list(option_string, question):
    raw_options = split_ignore_strings(option_string, ["\n"])
    if not is_matrix(question):
        log(Logging.INFO, "Setting display mode for "+ str(question))
        question.update({
            'displayMode': 'list'
        })
    for index, option in enumerate(raw_options):
        value = option.strip()
        if len(value) == 0:
            # Empty option, skip
            continue
        if value.lower() == "other":
            if not is_matrix(question):
                question.update({'displayMode': 'list+input'})
            continue
        split_value = partition_ignore_strings(value, "=")
        if len(split_value) > 1:
            add_option(split_value[0], split_value[1], question, index + 1)
        else:
            add_option(value, value, question, index + 1)

# Add an option node to a question
def add_option(value, label, question, index = 0):
    option_details = {
        'jcr:primaryType': 'cards:AnswerOption',
        'label': label,
        'value': clean_title(value).lower()
    }
    if index:
        option_details.update({"defaultOrder": index})
    answer_option = {clean_name(value).lower():
        add_option_properties(option_details, label)
    }
    question.update(answer_option)

# Add any additional properties thatare needed for an option node
def add_option_properties(option, label):
    base_label = label.lower().strip()
    if base_label == "none of the above":
        option['noneOfTheAbove'] = True
    if base_label == "n/a" or base_label == "not applicable" or base_label == "none":
        option['notApplicable'] = True
    return option



#=====================
# Conditional handling
#=====================

def process_conditional(self, questionnaire, row):
    needs_section = get_row_type_map(row).row_type not in (SECTION_TYPES + MATRIX_TYPES)
    if needs_section:
        questionnaire.push_section(create_new_section(questionnaire.question['name'] + "section", False), False)

    conditional_string = self.get_value(row)
    log(Logging.INFO, "Processing conditional " + conditional_string)
    condition_handle_brackets(questionnaire, questionnaire.parent, conditional_string)

    questionnaire.complete_section()

def condition_handle_brackets(questionnaire, condition_parent, conditional_string, index=0):
    log(Logging.INFO, "Handling brackets      " + conditional_string)
    if conditional_string.startswith("(") and conditional_string.endswith(")"):
        conditional_string = conditional_string[1:-1].strip()
    condition_handle_block(questionnaire, condition_parent, conditional_string, index)

# TODO: Better name?
def condition_handle_block(questionnaire, condition_parent, conditional_string, index=0):
    log(Logging.INFO, "Handling block         " + conditional_string)
    or_list = split_ignore_strings(conditional_string, [" or "])
    and_list = split_ignore_strings(conditional_string, [" and "])

    if len(or_list) > 1 and len (and_list) > 1:
        # Can't (easily) handle properly:
        # Log the warning and skip
        log(Logging.WARNING, "Invalid conditional found: " + conditional_string
            + "\nParsed as or: " + str(or_list)
            + "\nParsed as and: " + str(and_list)
            + "\nPlease seperate `and` and `or` statements into seperate groups using `()`"
            + "\nSkipping...")
        return

    if len(or_list) > 1:
        condition_handle_multiple(questionnaire, condition_parent, or_list, False, index)
    elif len(and_list) > 1:
        condition_handle_multiple(questionnaire, condition_parent, and_list, True, index)
    else:
        condition_handle_single(questionnaire, condition_parent, conditional_string, index)

def condition_handle_multiple(questionnaire, condition_parent, conditionals, require_all, index=0):
    log(Logging.INFO, "Handling multiple      " + str(conditionals))
    condition_parent.update({'conditionalGroup' + str(index): {
        'jcr:primaryType': 'cards:ConditionalGroup',
        'requireAll': require_all
    }})

    for i, condition in enumerate(conditionals):
        condition_handle_brackets(questionnaire, condition_parent['conditionalGroup' + str(index)], condition, i)

def condition_handle_single(questionnaire, condition_parent, conditional_string, index):
    log(Logging.INFO, "Handling single        " + conditional_string)
    OPERATORS_SINGLE = [
        "is empty",
        "is not empty",
    ]
    # Order matters since the first match will be used:
    # `=` must be before '<=` and `>=`
    # `<>`, `<=`, and `>=` must be before `<` and `>`
    OPERATORS_PAIR = [
        "=",
        "<=",
        ">=",
        "<>",
        "<",
        ">",
    ]
    OPERATORS_ALL = OPERATORS_SINGLE + OPERATORS_PAIR

    terms = split_ignore_strings(conditional_string, OPERATORS_ALL, 1)
    if len(terms) == 2:
        if (len(terms[1]) == 0):
            # Found a single operand conditional
            for operator in OPERATORS_SINGLE:
                if conditional_string.endswith(operator):
                    create_condition(questionnaire, condition_parent, index, terms[0], operator, terms[0])
        else:
            operator = conditional_string[len(terms[0]):-len(terms[1])].strip()
            create_condition(questionnaire, condition_parent, index, terms[0], operator, terms[1])

def create_condition(questionnaire, condition_parent, index, operand_a, operator, operand_b):
    log(Logging.INFO, "Creating condition     " + operand_a + " " + operator + " " + operand_b)
    if operand_a[0] == "\"" and operand_a[-1] == "\"":
        operand_a = operand_a[1:-1]
    if operand_b[0] == "\"" and operand_b[-1] == "\"":
        operand_b = operand_b[1:-1]

    # TODO: Do conditionals support arrays of values in operandA or operand_b?
    # If so, handle that case

    is_reference = operand_b in questionnaire.question_title_list

    result = {
        'jcr:primaryType': 'cards:Conditional',
        'operandA': {
            'jcr:primaryType': 'cards:ConditionalValue',
            'value': [operand_a.lower()],
            'isReference': True
        },
        'comparator' : operator,
        'operandB': {
            'jcr:primaryType': 'cards:ConditionalValue',
            'value': [operand_b.lower()],
            'isReference': is_reference
        }
    }

    # TODO: Do conditionals support arrays of values in operandA or operand_b?
    # If so, handle that case:
    # # If the operator is <>, make sure that all entries for operand_a meet that requirement
    # if (operator == "<>"):result['operandA']['requireAll'] = True

    condition_parent["condition" + str(index)] = result



#=================
# Run the importer
#=================

# Creates a JSON file that contains the tsv file as an cards:Questionnaire
def csv_to_json(title):
    log(Logging.RUN, "Importing {}".format(title))
    questionnaireState = QuestionnaireState()
    with open(title + '.csv', encoding="utf-8-sig") as csvfile:
        reader = csv.DictReader(csvfile, dialect='excel')
        for index, row in enumerate(reader):
            # Add 2 to index to match up with spreadsheet view:
            # +1 from index being 0 based, +1 from header row
            log(Logging.INFO, "Row {}".format(index + 2))
            if index == 0 and not Headers["QUESTIONNAIRE"].has_value(row):
                questionnaireState.add_questionnaire(create_new_questionnaire(title))
            for header in DefaultHeaders.values():
                if header.has_value(row):
                    header.handler(header, questionnaireState, row)
            questionnaireState.complete_question()

    questionnaireState.complete_questionnaire()

    for q in questionnaireState.questionnaires:
        name = clean_name(q['title'])
        with open(name + '.json', 'w') as jsonFile:
            json.dump(q, jsonFile, indent='\t')
        os.system("python3 ../JSON-to-XML/json_to_xml.py '" + name + ".json' > '" + name + ".xml'")

CLI = argparse.ArgumentParser()
CLI.add_argument("--forms", nargs="*", type=str, required=True)
CLI.add_argument("--paginate", nargs=1, type=bool, default=False)
CLI.add_argument("--subject-types", nargs=1, type=str, default="/SubjectTypes/Patient/Visit")
CLI.add_argument("--max-answers", nargs=1, type=int, default=1)
CLI.add_argument("--max-per-subject", nargs=1, type=int, default=1)

args = CLI.parse_args()

for title in args.forms:
    Headers = DefaultHeaders
    Options = args
    Options.logging = Logging.WARNING
    csv_to_json(title)
