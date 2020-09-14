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
        # No title is needed because only a single lfs:Conditional will be created
        insert_conditional(parent_logic, question, '')


# Updates the question with lfs:Conditionals from the output of prepare_conditional
def insert_conditional(parent_logic, question, title):
    # Split the conditional into two operands and an operator
    parent_logic = parent_logic.partition('=')
    operand_a = parent_logic[0].strip()
    operand_b = parent_logic[2].strip()
    # If the first operand is a comma-separated list, create a separate conditional for each
    # Enclose the conditionals in a lfs:ConditionalGroup
    if ',' in operand_a:
        question.update({'conditionalGroup': {'jcr:primaryType': 'lfs:ConditionalGroup'}})
        # The keyword 'all' in the ParentLogic should correspond to 'requireAll' == true
        # If it is present, remove it from the operand and add 'requireAll' to the conditional group
        if 'all' in operand_a:
            operand_a = operand_a[:-3]
            question['conditionalGroup'].update({'requireAll': True})
        operand_a_list = list(operand_a.replace(' ', '').split(','))
        for index, item in enumerate(operand_a_list):
            question['conditionalGroup'].update(create_conditional(item, operand_b, 'condition' + str(index)))
    # If the second operand is a comma-separated list, create a separate conditional for each
    # Enclose the conditionals in a lfs:ConditionalGroup
    elif ',' in operand_b:
        question.update({'conditionalGroup': {'jcr:primaryType': 'lfs:ConditionalGroup'}})
        operand_b_list = list(operand_b.replace(' ', '').split(','))
        for index, item in enumerate(operand_b_list):
            question['conditionalGroup'].update(create_conditional(operand_a, item, 'condition' + str(index)))
    else:
        question.update(create_conditional(operand_a, operand_b, 'condition' + title))


# Returns a dict object that is formatted as an lfs:Conditional
def create_conditional(operand_a, operand_b, title):
    is_reference = False
    # NOTE: IN THE CASE OF A REFRENCE TO A QUESTION WHOSE POSSIBLE VALUES ARE YES/NO/OTHER
    # YOU WILL HAVE TO MANUALLY CHANGE THE CONDITIONALS SINCE THEY WILL BE REPLACED WITH T/F
    if operand_b.lower() == 'yes':
        operand_b_updated = "true"
    elif operand_b.lower() == 'no':
        operand_b_updated = "false"
    else:
        operand_b_updated = operand_b
        is_reference = True
    return {title: {
        'jcr:primaryType': 'lfs:Conditional',
        'operandA': {
            'jcr:primaryType': 'lfs:ConditionalValue',
            'value': [operand_a],
            'isReference': True
        },
        'comparator': '=',
        'operandB': {
            'jcr:primaryType': 'lfs:ConditionalValue',
            'value': [operand_b_updated],
            'isReference': is_reference
        }
    }}


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


# Creates lfs:AnswerOptions from the CSV in 'Categorical List'
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
        elif option.lower() == 'other':
            question.update({'displayMode': 'list+input'})
        else:
            answer_option = {option: {'jcr:primaryType': 'lfs:AnswerOption',
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


# Converts the data type in 'UserFormatType' to one supported in LFS
DATA_TO_LFS_TYPE = {
    'date': 'date',
    'integer': 'long',
    'yes,no': "boolean",
    'age (months:days)': 'text' # TODO: Switch this to an interval question when it is supported
}
def convert_to_LFS_data_type(argument):
    return DATA_TO_LFS_TYPE.get(argument.strip().lower(), 'text')


def insert_description(question, row):
    question.update({
      'description': row['Description'].strip()
      })


# Creates a JSON file that contains the tsv file as an lfs:Questionnaire
def tsv_to_json(title):
    questionnaire = {}
    questionnaire['jcr:primaryType'] = 'lfs:Questionnaire'
    questionnaire['title'] = title + ' Data'

    with open(title + '.tsv') as tsvfile:
        reader = csv.DictReader(tsvfile, dialect='excel-tab')
        for row in reader:
            question = row['nameShort'].strip()
            if question:
                questionnaire[question] = {
                    'jcr:primaryType': 'lfs:Question',
                    'text': row['nameFull'].strip() or question,
                    'dataType': convert_to_LFS_data_type(row['UserFormatType'])
                }
                if row['RangeMinValid'] != '' and row['RangeMinValid'] != None:
                    insert_range(questionnaire[question], row)
                if row['MissingData']:
                    insert_min_answers(questionnaire[question], row)
                if row['Categorical list']:
                    if len(options_list(row['Categorical list'])) == 1:
                        questionnaire[question].update({'dataType': convert_to_LFS_data_type(row['Categorical list'])})
                    else:
                        insert_options(questionnaire[question], row)
                if row['Description'] != '':
                    insert_description(questionnaire[question], row)
                if row['UserFormatType'] == 'Text (categorical list)' or row['UserFormatType'] == 'cat list':
                    questionnaire[question].update({'displayMode': 'list'})
                if row['ParentLogic'] != '':
                    previous_data = questionnaire[question]
                    questionnaire.update({question + 'Section': {
                        'jcr:primaryType': 'lfs:Section'
                    }})
                    questionnaire[question + 'Section'][question] = previous_data
                    prepare_conditional(questionnaire[question + 'Section'], row)

    with open(title + '.json', 'w') as jsonFile:
        json.dump(questionnaire, jsonFile, indent='\t')


titles = ['Q1Variables', '0-5NDVariables', '6-21NDVariables']
for title in titles:
    tsv_to_json(title)
