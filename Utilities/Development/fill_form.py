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
import uuid
import requests

CARDS_URL = "http://localhost:8080"
if "CARDS_URL" in os.environ:
  CARDS_URL = os.environ["CARDS_URL"].rstrip('/')

ADMIN_PASSWORD = "admin"
if "ADMIN_PASSWORD" in os.environ:
  ADMIN_PASSWORD = os.environ["ADMIN_PASSWORD"]

def get_jcr_uuid_for_path(path):
  resp = requests.get(CARDS_URL + path + ".json", auth=('admin', ADMIN_PASSWORD))
  return resp.json()['jcr:uuid']

def createFormNode(questionnaire_path, subject_path):
  questionnaire_jcr_uuid = get_jcr_uuid_for_path(questionnaire_path)
  subject_path_jcr_uuid = get_jcr_uuid_for_path(subject_path)

  creation_form_data = []
  creation_form_data.append(("jcr:primaryType", (None, "cards:Form")))

  creation_form_data.append(("questionnaire", (None, questionnaire_jcr_uuid)))
  creation_form_data.append(("questionnaire@TypeHint", (None, "Reference")))

  creation_form_data.append(("subject", (None, subject_path_jcr_uuid)))
  creation_form_data.append(("subject@TypeHint", (None, "Reference")))

  new_form_jcr_path = "/Forms/" + str(uuid.uuid4())
  resp = requests.post(CARDS_URL + new_form_jcr_path, files=tuple(creation_form_data), auth=('admin', ADMIN_PASSWORD))

  if resp.status_code in range(200, 300):
    return new_form_jcr_path
  else:
    raise Exception("ERROR: Cound not create {}".format(new_form_jcr_path))

def find_answer_nodes(node, qa_map={}):
  for key in node:
    if type(node[key]) == dict:
      if node[key]['jcr:primaryType'] == 'cards:AnswerSection':
        find_answer_nodes(node[key], qa_map=qa_map)
      elif node[key]['sling:resourceSuperType'] == 'cards/Answer':
        answer_path = node[key]['@path']
        question_text = node[key]['question']['text']
        qa_map[question_text] = answer_path
  return qa_map

def post_answer(answer_node_path, value, value_type):
  creation_form_data = []
  creation_form_data.append(("value", (None, value)))
  creation_form_data.append(("value@TypeHint", (None, value_type)))
  resp = requests.post(CARDS_URL + answer_node_path, auth=('admin', ADMIN_PASSWORD), files=tuple(creation_form_data))
  if resp.status_code != 200:
    raise Exception("{} was returned upon answer submission".format(resp.status_code))

def fill_form(subject_path, questionnaire_path, provided_answers):
  form_path = createFormNode(questionnaire_path, subject_path)
  form_structure = requests.get(CARDS_URL + form_path + ".deep.json", auth=('admin', ADMIN_PASSWORD)).json()
  question_to_answer_nodes = find_answer_nodes(form_structure)
  for qtext in question_to_answer_nodes:
    if qtext not in provided_answers:
      continue
    apath = question_to_answer_nodes[qtext]
    avalue = provided_answers[qtext][0]
    atype = provided_answers[qtext][1]
    post_answer(apath, avalue, atype)
