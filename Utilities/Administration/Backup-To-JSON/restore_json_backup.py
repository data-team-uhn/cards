#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
"""

import os
import sys
import json
import requests

if len(sys.argv) < 4:
  print("Usage: python3 restore_json_backup.py BACKUP_DIRECTORY FORM_LIST_FILE SUBJECT_LIST_FILE")
  sys.exit(1)

BACKUP_DIRECTORY = sys.argv[1]
FORM_LIST_FILE = sys.argv[2]
SUBJECT_LIST_FILE = sys.argv[3]

CARDS_URL = "http://localhost:8080"
if "CARDS_URL" in os.environ:
  CARDS_URL = os.environ["CARDS_URL"].rstrip('/')

ADMIN_PASSWORD = "admin"
if "ADMIN_PASSWORD" in os.environ:
  ADMIN_PASSWORD = os.environ["ADMIN_PASSWORD"]

with open(FORM_LIST_FILE, 'r') as f:
  FORM_LIST = json.loads(f.read())

with open(SUBJECT_LIST_FILE, 'r') as f:
  SUBJECT_LIST = json.loads(f.read())

def getSubject(subjectPath):
  subjectBasename = os.path.basename(subjectPath)
  with open(os.path.join(BACKUP_DIRECTORY, "Subjects", subjectBasename + ".json"), 'r') as f:
    subjectData = json.loads(f.read())
  if subjectData["@path"] == subjectPath:
    return subjectData

def getForm(formPath):
  formBasename = os.path.basename(formPath)
  with open(os.path.join(BACKUP_DIRECTORY, "Forms", formBasename + ".json"), 'r') as f:
    formData = json.loads(f.read())
  return formData

def getJcrUuid(nodePath):
  resp = requests.get(CARDS_URL + nodePath + '.json', auth=('admin', ADMIN_PASSWORD))
  return resp.json()['jcr:uuid']

def createSubjectInJcr(path, subjectTypePath, identifier):
  params = {}
  params['jcr:primaryType'] = (None, 'cards:Subject')
  params['identifier'] = (None, identifier)
  params['type'] = (None, getJcrUuid(subjectTypePath))
  params['type@TypeHint'] = (None, 'Reference')
  resp = requests.post(CARDS_URL + path, auth=('admin', ADMIN_PASSWORD), files=params)
  if resp.status_code != 201:
    raise Exception("Failed to create Subject in JCR")

def createFormInJcr(path, questionnairePath, subjectPath):
  params = {}
  params['jcr:primaryType'] = (None, 'cards:Form')
  params['questionnaire'] = (None, questionnairePath)
  params['questionnaire@TypeHint'] = (None, 'Reference')
  params['subject'] = (None, subjectPath)
  params['subject@TypeHint'] = (None, 'Reference')
  resp = requests.post(CARDS_URL + path, auth=('admin', ADMIN_PASSWORD), files=params)
  if resp.status_code != 201:
    raise Exception("Failed to create Form node in JCR")

def createAnswerSectionInJcr(path, questionnaireSectionPath):
  params = {}
  params['jcr:primaryType'] = (None, 'cards:AnswerSection')
  params['section'] = (None, getJcrUuid(questionnaireSectionPath))
  params['section@TypeHint'] = (None, 'Reference')
  resp = requests.post(CARDS_URL + path, auth=('admin', ADMIN_PASSWORD), files=params)
  if resp.status_code not in range(200, 300):
    raise Exception("Failed to create AnswerSection node in JCR")

def createIntermediateAnswerSectionsInJcr(answerNodePath, questionNodePath):
  # answerNodePath follows a format of /Forms/<FORM NODE>/<ZERO OR MORE SECTION NODES>/<ANSWER NODE>
  # questionNodePath follows a format of /Questionnaires/<QUESTION NODE>/<ZERO OR MORE SECTION NODES>/<QUESTION NODE>
  formNode = answerNodePath.split('/')[2]
  questionnaireNode = questionNodePath.split('/')[2]
  nodesAfterForm = answerNodePath.split('/')[2:]
  nodesAfterQuestionnaire = questionNodePath.split('/')[2:]
  formSectionNodes = nodesAfterForm[1:-1]
  questionnaireSectionNodes = nodesAfterQuestionnaire[1:-1]
  if len(formSectionNodes) != len(questionnaireSectionNodes):
    raise Exception("Form section doesn't have a matching Questionnaire section")
  for i in range(len(formSectionNodes)):
    thisSectionFPath = '/'.join(['', 'Forms', formNode] + formSectionNodes[0:i+1])
    thisSectionQPath = '/'.join(['', 'Questionnaires', questionnaireNode] + questionnaireSectionNodes[0:i+1])
    print("Creating answer section at {} for question section {}".format(thisSectionFPath, thisSectionQPath))
    createAnswerSectionInJcr(thisSectionFPath, thisSectionQPath)

CARDS_TYPE_TO_SLING_TYPE_HINT = {}
CARDS_TYPE_TO_SLING_TYPE_HINT['cards:BooleanAnswer'] = "long"
CARDS_TYPE_TO_SLING_TYPE_HINT['cards:TextAnswer'] = "string"
CARDS_TYPE_TO_SLING_TYPE_HINT['cards:LongAnswer'] = "long"
CARDS_TYPE_TO_SLING_TYPE_HINT['cards:DoubleAnswer'] = "double"
CARDS_TYPE_TO_SLING_TYPE_HINT['cards:DecimalAnswer'] = "decimal"
CARDS_TYPE_TO_SLING_TYPE_HINT['cards:DateAnswer'] = "date"
CARDS_TYPE_TO_SLING_TYPE_HINT['cards:TimeAnswer'] = "string"
CARDS_TYPE_TO_SLING_TYPE_HINT['cards:PedigreeAnswer'] = "string"
CARDS_TYPE_TO_SLING_TYPE_HINT['cards:VocabularyAnswer'] = "string"
CARDS_TYPE_TO_SLING_TYPE_HINT['cards:ChromosomeAnswer'] = "string"
CARDS_TYPE_TO_SLING_TYPE_HINT['cards:FileAnswer'] = "string" # TODO Properly handle FileAnswers

def createAnswerInJcr(answerNodePath, questionNodePath, primaryType, value):
  params = []
  params.append(('jcr:primaryType', (None, primaryType)))
  params.append(('question', (None, getJcrUuid(questionNodePath))))
  params.append(('question@TypeHint', (None, 'Reference')))
  if type(value) is list:
    for item in value:
      params.append(('value', (None, str(item))))
    params.append(('value@TypeHint', (None, CARDS_TYPE_TO_SLING_TYPE_HINT[primaryType] + "[]")))
  else:
    params.append(('value', (None, str(value))))
    params.append(('value@TypeHint', (None, CARDS_TYPE_TO_SLING_TYPE_HINT[primaryType])))
  resp = requests.post(CARDS_URL + answerNodePath, auth=('admin', ADMIN_PASSWORD), files=tuple(params))
  if resp.status_code != 201:
    raise Exception("Failed to create Answer node in JCR")

# Sort SUBJECT_LIST so that parents are created before children
SUBJECT_LIST = sorted(SUBJECT_LIST, key=lambda x: x.count('/'))

# Restore the Subjects
for subjectPath in SUBJECT_LIST:
  subject = getSubject(subjectPath)
  print("Creating Subject (type={}) at: {}".format(subject["type"], subjectPath))
  createSubjectInJcr(subjectPath, subject["type"], subject["identifier"])

# After the Subjects have been restored, we can begin to restore the Forms
for formPath in FORM_LIST:
  form = getForm(formPath)
  print("Creating Form (subject={}, questionnaire={}) at: {}".format(form["subject"], form["questionnaire"], formPath))
  createFormInJcr(formPath, form["questionnaire"], form["subject"])
  # For all the responses to this Form...
  for responsePath in form["responses"]:
    # ...first create all (if any) of the intermediate AnswerSection nodes
    associatedQuestion = form["responses"][responsePath]["question"]
    dataType = form["responses"][responsePath]["jcr:primaryType"]
    dataValue = form["responses"][responsePath]["value"]
    createIntermediateAnswerSectionsInJcr(responsePath, associatedQuestion)
    createAnswerInJcr(responsePath, associatedQuestion, dataType, dataValue)
