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

import io
import os
import json
import argparse
import requests

argparser = argparse.ArgumentParser()
argparser.add_argument('--backup_directory', help='Path to backup directory', type=str, required=True)
argparser.add_argument('--form_list_file', help='Path to the Form list file', type=str, required=True)
argparser.add_argument('--subject_list_file', help='Path to the Subject list file', type=str, required=True)
args = argparser.parse_args()

BACKUP_DIRECTORY = args.backup_directory
FORM_LIST_FILE = args.form_list_file
SUBJECT_LIST_FILE = args.subject_list_file

CARDS_URL = "http://localhost:8080"
if "CARDS_URL" in os.environ:
  CARDS_URL = os.environ["CARDS_URL"].rstrip('/')

ADMIN_PASSWORD = "admin"
if "ADMIN_PASSWORD" in os.environ:
  ADMIN_PASSWORD = os.environ["ADMIN_PASSWORD"]

with open(FORM_LIST_FILE, 'r') as f:
  FORM_LIST = json.loads(f.read())
FORM_LIST = [f[0] for f in FORM_LIST]

with open(SUBJECT_LIST_FILE, 'r') as f:
  SUBJECT_LIST = json.loads(f.read())
SUBJECT_LIST = [s[0] for s in SUBJECT_LIST]

def getSubject(subjectPath):
  subjectBasename = os.path.basename(subjectPath)
  subjectJsonPath = os.path.join(BACKUP_DIRECTORY, "Subjects", subjectBasename + ".json")
  if not os.path.exists(subjectJsonPath):
    return None
  with open(subjectJsonPath, 'r') as f:
    subjectData = json.loads(f.read())
  if subjectData["@path"] == subjectPath:
    return subjectData

def getForm(formPath):
  formBasename = os.path.basename(formPath)
  formJsonPath = os.path.join(BACKUP_DIRECTORY, "Forms", formBasename + ".json")
  if not os.path.exists(formJsonPath):
    return None
  with open(formJsonPath, 'r') as f:
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
CARDS_TYPE_TO_SLING_TYPE_HINT['cards:FileAnswer'] = "string"
CARDS_TYPE_TO_SLING_TYPE_HINT['cards:DicomAnswer'] = "string"
CARDS_TYPE_TO_SLING_TYPE_HINT['cards:ResourceAnswer'] = "string"
CARDS_TYPE_TO_SLING_TYPE_HINT['cards:PhoneAnswer'] = "string"

FILE_LIKE_ANSWER_TYPES = []
FILE_LIKE_ANSWER_TYPES.append("cards:FileAnswer")
FILE_LIKE_ANSWER_TYPES.append("cards:DicomAnswer")

def createAnswerInJcr(answerNodePath, questionNodePath, primaryType, value, extraValues={}, fileDataSha256=None):
  params = []
  params.append(('jcr:primaryType', (None, primaryType)))
  params.append(('question', (None, getJcrUuid(questionNodePath))))
  params.append(('question@TypeHint', (None, 'Reference')))
  if type(value) is list:
    for item in value:
      params.append(('value', (None, str(item))))
    params.append(('value@TypeHint', (None, CARDS_TYPE_TO_SLING_TYPE_HINT[primaryType] + "[]")))
  elif value is not None:
    params.append(('value', (None, str(value))))
    params.append(('value@TypeHint', (None, CARDS_TYPE_TO_SLING_TYPE_HINT[primaryType])))

  for extraValueKey in extraValues:
    params.append((extraValueKey, (None, str(extraValues[extraValueKey]))))

  if primaryType in FILE_LIKE_ANSWER_TYPES and type(fileDataSha256) == dict:
    for jcrFilename in fileDataSha256:
      blobFilePath = os.path.join(BACKUP_DIRECTORY, "blobs", fileDataSha256[jcrFilename] + ".blob")
      f_blob = open(blobFilePath, 'rb')
      params.append((jcrFilename, f_blob))

  resp = requests.post(CARDS_URL + answerNodePath, auth=('admin', ADMIN_PASSWORD), files=tuple(params))

  # Close any files that were uploaded
  for p in params:
    if type(p[1]) == io.BufferedReader:
      p[1].close()

  if resp.status_code != 201:
    raise Exception("Failed to create Answer node in JCR")

# Sort SUBJECT_LIST so that parents are created before children
SUBJECT_LIST = sorted(SUBJECT_LIST, key=lambda x: x.count('/'))

# Restore the Subjects
for subjectPath in SUBJECT_LIST:
  subject = getSubject(subjectPath)
  if subject is None:
    print("Warning: No JSON file for Subject {} was found. Perhaps the Subject was created just outside of the snapshot window.".format(subjectPath))
    continue
  print("Creating Subject (type={}) at: {}".format(subject["type"], subjectPath))
  createSubjectInJcr(subjectPath, subject["type"], subject["identifier"])

# After the Subjects have been restored, we can begin to restore the Forms
for formPath in FORM_LIST:
  form = getForm(formPath)
  if form is None:
    print("Warning: No JSON file for Form {} was found. Perhaps the Form was created just outside of the snapshot window.".format(formPath))
    continue
  if form["subject"] not in SUBJECT_LIST:
    print("Warning: Skipping the creation of Form {} as its associated Subject {} cannot be found.".format(formPath, form["subject"]))
    continue
  print("Creating Form (subject={}, questionnaire={}) at: {}".format(form["subject"], form["questionnaire"], formPath))
  createFormInJcr(formPath, form["questionnaire"], form["subject"])
  # For all the responses to this Form...
  for responsePath in form["responses"]:
    # ...first create all (if any) of the intermediate AnswerSection nodes
    associatedQuestion = form["responses"][responsePath]["question"]
    dataType = form["responses"][responsePath]["jcr:primaryType"]

    dataValue = None
    if "value" in form["responses"][responsePath]:
      dataValue = form["responses"][responsePath]["value"]

    extraValues = {}
    if "note" in form["responses"][responsePath]:
      extraValues["note"] = form["responses"][responsePath]["note"]
    if "image" in form["responses"][responsePath]:
      extraValues["image"] = form["responses"][responsePath]["image"]

    fileSha256 = None
    if "fileDataSha256" in form["responses"][responsePath]:
      fileSha256 = form["responses"][responsePath]["fileDataSha256"]
    createIntermediateAnswerSectionsInJcr(responsePath, associatedQuestion)
    createAnswerInJcr(responsePath, associatedQuestion, dataType, dataValue, extraValues, fileSha256)
