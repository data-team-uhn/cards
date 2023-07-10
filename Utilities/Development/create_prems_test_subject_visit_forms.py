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

import sys
import json
import time

import fill_form
import create_subject_for_type

IDENTIFIER_ID = sys.argv[1]

with open("PREMs-Testing/OED_responses.json", 'r') as f_json:
  OED_ANSWERS = json.load(f_json)

VISIT_INFORMATION_ANSWERS = {}
VISIT_INFORMATION_ANSWERS['Surveys completed'] = [1, "Long"]
VISIT_INFORMATION_ANSWERS['Surveys submitted'] = [1, "Long"]

patient_subject_path = create_subject_for_type.create_subject_for_type("/Subjects", "P" + IDENTIFIER_ID, "/SubjectTypes/Patient")
visit_subject_path = create_subject_for_type.create_subject_for_type(patient_subject_path, "V" + IDENTIFIER_ID, "/SubjectTypes/Patient/Visit")

# Fill out the OED Form
fill_form.fill_form(visit_subject_path, "/Questionnaires/OED", OED_ANSWERS)

# A small delay to simulate the time between when the user fills out their surveys and when the submit button is clicked
time.sleep(3)

# Mark the surveys for this visit as completed and submitted
fill_form.fill_form(visit_subject_path, "/Questionnaires/Visit information", VISIT_INFORMATION_ANSWERS)
