#!/usr/bin/env python
# -*- coding: utf-8 -*-

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

import os
import sys
import requests

CARDS_URL = "http://localhost:8080"
if "CARDS_URL" in os.environ:
  CARDS_URL = os.environ["CARDS_URL"].rstrip('/')

ADMIN_PASSWORD = "admin"
if "ADMIN_PASSWORD" in os.environ:
  ADMIN_PASSWORD = os.environ["ADMIN_PASSWORD"]

RESPONSES_CSV_FILEPATHS = []
for i in range(1, len(sys.argv)):
  RESPONSES_CSV_FILEPATHS.append(sys.argv[i])

subject_types_query = requests.get(CARDS_URL + "/query?query=SELECT * FROM [cards:SubjectType] as n order by n.'cards:defaultOrder'", auth=('admin', ADMIN_PASSWORD))
if subject_types_query.status_code != 200:
  print("FAIL")
  sys.exit(-1)

visit_type_uuid = None
subject_types_list = subject_types_query.json()['rows']
for subjectType in subject_types_list:
  if subjectType['@name'] == "Visit":
    visit_type_uuid = subjectType['jcr:uuid']

if visit_type_uuid is None:
  print("FAIL")
  sys.exit(-1)

visits_query = requests.get(CARDS_URL + "/Subjects.paginate?fieldname=type&fieldvalue=" + visit_type_uuid + "&offset=0&limit=1000000000000000&req=0", auth=('admin', ADMIN_PASSWORD))
if visits_query.status_code != 200:
  print("FAIL")
  sys.exit(-1)

visits_list = visits_query.json()['rows']
for visit in visits_list:
  #print(visit['fullIdentifier'])
  patientIdentifier = visit['fullIdentifier'].split(' / ')[0]
  visitIdentifier = visit['fullIdentifier'].split(' / ')[1]
  print("Handling Visit subject: {} / {}".format(patientIdentifier, visitIdentifier))
  for csvFilepath in RESPONSES_CSV_FILEPATHS:
    questionnaireName = csvFilepath.split('/')[-1].split('.')[0]
    print("\tAdding answers from {} as questionnaire: {} ... ".format(csvFilepath, questionnaireName), end='')
    with open(csvFilepath, 'r') as f_csv_template:
      rendered_csv = f_csv_template.read().replace("{{PATIENT_IDENTIFIER}}", patientIdentifier).replace("{{VISIT_IDENTIFIER}}", visitIdentifier)
      answer_submission_response = requests.post(CARDS_URL + "/Forms/", auth=('admin', ADMIN_PASSWORD), files=((':data', rendered_csv), (':questionnaire', '/Questionnaires/' + questionnaireName), (':subjectType', '/SubjectTypes/Patient'), (':subjectType', '/SubjectTypes/Patient/Visit'), (':patch', 'true')))
      if answer_submission_response.status_code == 200:
        print("[DONE]")
      else:
        print("[FAIL]")
        sys.exit(-1)
