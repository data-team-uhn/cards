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
import regex


# Creates conditional statements from ParentLogic to be used by insert_conditional
def prepare_conditional(question, row):
    # Remove the word 'if' from the beginning of the logical statement
    parent_logic = row['ParentLogic']
    if parent_logic.lower().startswith('if'):
        parent_logic = parent_logic[3:]
    # Split statement into two at the 'or'
    if parent_logic.rfind('or') != -1:
        parent_logic = parent_logic.partition('or')
        # If one of the resulting statements is incomplete
        # Such as in the case of splitting "if CVLT-C or CVLT-II=yes"
        # Copy what's after the equals sign to the incomplete part
        if "=" not in parent_logic[0]:
            insert_conditional(parent_logic[0] + parent_logic[2].partition("=")[1] + parent_logic[2].partition("=")[2], question, '1')
        else:
            insert_conditional(parent_logic[0], question, '1')
        insert_conditional(parent_logic[2], question, '2')
    else:
        # No title is needed because only a single cards:Conditional will be created
        insert_conditional(parent_logic, question, '')


# Updates the question with cards:Conditionals from the output of prepare_conditional
def insert_conditional(parent_logic, question, title):
    # Split the conditional into two operands and an operator
    parent_logic = partition_parent_logic(parent_logic)
    operand_a = parent_logic[0].strip()
    operator = parent_logic[1]
    operand_b = parent_logic[2].strip()
    # If the first operand is a comma-separated list, create a separate conditional for each
    # Enclose the conditionals in a cards:ConditionalGroup
    if ',' in operand_a:
        question.update({'conditionalGroup': {'jcr:primaryType': 'cards:ConditionalGroup'}})
        # The keyword 'all' in the ParentLogic should correspond to 'requireAll' == true
        # If it is present, remove it from the operand and add 'requireAll' to the conditional group
        if 'all' in operand_a:
            operand_a = operand_a[:-3]
            question['conditionalGroup'].update({'requireAll': True})
        operand_a_list = list(operand_a.replace(' ', '').split(','))
        for index, item in enumerate(operand_a_list):
            question['conditionalGroup'].update(create_conditional(item, operator, operand_b, 'condition' + str(index)))
    # If the second operand is a comma-separated list, create a separate conditional for each
    # Enclose the conditionals in a cards:ConditionalGroup
    elif ',' in operand_b:
        question.update({'conditionalGroup': {'jcr:primaryType': 'cards:ConditionalGroup'}})
        # The keyword 'all' in the ParentLogic should correspond to 'requireAll' == true
        # If it is present, remove it from the operand and add 'requireAll' to the conditional group
        if 'all' in operand_b:
            operand_b = operand_b[:-3]
            question['conditionalGroup'].update({'requireAll': True})
        operand_b_list = list(operand_b.replace(' ', '').split(','))
        for index, item in enumerate(operand_b_list):
            question['conditionalGroup'].update(create_conditional(operand_a, operator, item, 'condition' + str(index)))
    else:
        question.update(create_conditional(operand_a, operator, operand_b, 'condition' + title))

# Split the parent_logic entry into 3 parts: The first operand, the operator and the second operand.
def partition_parent_logic(parent_logic):
    match = regex.search('=|<>|<|>', parent_logic)
    if not match:
        # No operator detected, return everything as a single operand
        return parent_logic, '', ''

    seperator = match.group(0)
    parts = regex.split(seperator, parent_logic, 1)
    return parts[0], seperator, parts[1]

# Returns a dict object that is formatted as an cards:Conditional
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
    result = {
        'jcr:primaryType': 'cards:Conditional',
        'operandA': {
            'jcr:primaryType': 'cards:ConditionalValue',
            'value': [operand_a.lower()],
            'isReference': True
        },
        'comparator': operator,
        'operandB': {
            'jcr:primaryType': 'cards:ConditionalValue',
            'value': [operand_b_updated],
            'isReference': is_reference
        }
    }
    return {title: result}


# Adds a minAnswers property if 'MissingData' contains the keyword 'illegal'
def insert_min_answers(question, row):
    if row['MissingData'].lower() == 'illegal':
        question.update({'minAnswers': 1})


def options_list(categorical_list):
    split_character = ','
    if '(' in categorical_list:
        categorical_list = categorical_list.replace(')', '')
        categorical_list = categorical_list.replace('(', '')
    if '/' in categorical_list:
        if ',' in categorical_list:
            categorical_list = categorical_list.replace('/', '-')
        else:
            split_character = '/'
    option_list = list(categorical_list.split(split_character))
    return option_list


# Creates cards:AnswerOptions from the CSV in 'Categorical List'
def insert_options(question, row):
    option_list = options_list(row['Categorical list'])
    for option in option_list:
        option = option.strip()
        value = option
        if 'or ' in option:
            option = option.replace('or ', '')
        if ':' in option:
            option = option.replace(':', '')
        option = option.replace(' ', '').strip()
        if '.' in option:
            option = option.replace('.', '-')
        if (option.lower() == 'yes' or option.lower() == 'no') and len(option_list) == 2:
                question.update({'dataType': 'boolean'})
        elif 'other' in option.lower():
            question.update({'displayMode': 'list+input'})
            print(question)
        else:
            answer_option = {option: {'jcr:primaryType': 'cards:AnswerOption',
                                      'label': value,
                                      'value': value
            }}
            question.update(answer_option)


# Creates minValue and maxValue properties on a question from 'RangeMinValid' and 'RangeMaxValid'
def insert_range(question, row):
    question.update({
      'minValue': float(row['RangeMinValid']),
      'maxValue': float(row['RangeMaxValid'])
      })


# Converts the data type in 'UserFormatType' to one supported in CARDS
DATA_TO_CARDS_TYPE = {
    'date': 'date',
    'integer': 'long',
    'yes,no': "boolean",
    'age (months:days)': 'text' # TODO: Switch this to an interval question when it is supported
}
def convert_to_CARDS_data_type(userFormat, categorical_list):
    result = DATA_TO_CARDS_TYPE.get(userFormat.strip().lower(), 'text')
    if categorical_list:
        result = DATA_TO_CARDS_TYPE.get(categorical_list.strip().lower(), result)
    return result


# Creates a JSON file that contains the tsv file as an cards:Questionnaire
def tsv_to_json(title):
    questionnaire = {}
    questionnaire['jcr:primaryType'] = 'cards:Questionnaire'
    questionnaire['title'] = title + ' Data'

    with open(title + '.tsv') as tsvfile:
        reader = csv.DictReader(tsvfile, dialect='excel-tab')
        for row in reader:
            question = row['nameShort'].strip().lower()
            if question and (row['UserFormatType'] or row['Categorical list']):
                questionnaire[question] = {
                    'jcr:primaryType': 'cards:Question',
                    'text': row['nameFull'].strip() or question,
                    'dataType': convert_to_CARDS_data_type(row['UserFormatType'], row['Categorical list'])
                }
                if row['RangeMinValid'] != '' and row['RangeMinValid'] != None:
                    insert_range(questionnaire[question], row)
                if row['MissingData']:
                    insert_min_answers(questionnaire[question], row)
                if row['Description']:
                    questionnaire[question].update({ 'description': row['Description'].strip() })
                if row['UserFormatType'] == 'Text (categorical list)' or row['UserFormatType'] == 'cat list':
                    questionnaire[question].update({'displayMode': 'list'})
                if row['Categorical list'] and not DATA_TO_CARDS_TYPE.get(row['Categorical list'].strip().lower()):
                        insert_options(questionnaire[question], row)
                if row['ParentLogic'] != '':
                    previous_data = questionnaire[question]
                    questionnaire.update({question + 'Section': {
                        'jcr:primaryType': 'cards:Section'
                    }})
                    questionnaire[question + 'Section'][question] = previous_data
                    prepare_conditional(questionnaire[question + 'Section'], row)
                    # The presence of a conditional will also prevent the question from being inserted into the main thing
                    del questionnaire[question]


    with open(title + '.json', 'w') as jsonFile:
        json.dump(questionnaire, jsonFile, indent='\t')


titles = ['QIVariables', '0-5NDVariables', '6-21NDVariables']
for title in titles:
    tsv_to_json(title)
