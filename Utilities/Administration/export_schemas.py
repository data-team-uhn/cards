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
import json
import string
import argparse
import urllib.parse
import requests
from requests.auth import HTTPBasicAuth

argparser = argparse.ArgumentParser()
argparser.add_argument('--download_dir', help='Directory to where exported JSON schema files should be saved', required=True)
argparser.add_argument('--verbose', help='Enable verbose debug logging', action='store_true')
args = argparser.parse_args()

CARDS_URL = "http://localhost:8080"
if "CARDS_URL" in os.environ:
  CARDS_URL = os.environ["CARDS_URL"].rstrip('/')

ADMIN_PASSWORD = "admin"
if "ADMIN_PASSWORD" in os.environ:
  ADMIN_PASSWORD = os.environ["ADMIN_PASSWORD"]

SAFE_FILE_NAME_CHARS = string.ascii_letters + string.digits + "_-. "
def sanitizeFileName(file_name):
  clean_file_name = ""
  for c in str(file_name):
    if c in SAFE_FILE_NAME_CHARS:
      clean_file_name += c
  return clean_file_name

def getQuestionnaireNames():
  r = requests.get(CARDS_URL + "/Questionnaires.paginate?limit=10000", auth=HTTPBasicAuth("admin", ADMIN_PASSWORD))
  if r.status_code != 200:
    raise Exception("/Questionnaires.paginate?limit=10000 did not return HTTP 200")
  questionnaire_names = [row['@name'] for row in r.json()['rows']]
  return questionnaire_names

def getQuestionnaireSchema(questionnaire_name):
  r = requests.get(CARDS_URL + "/Questionnaires/" + urllib.parse.quote(questionnaire_name) + ".bare.deep.-identify.json", auth=HTTPBasicAuth("admin", ADMIN_PASSWORD))
  if r.status_code != 200:
    raise Exception("Could not get questionnaire schema for {}".format(questionnaire_name))
  return r.json()

questionnaire_names = getQuestionnaireNames()

for questionnaire_name in questionnaire_names:
  schema = getQuestionnaireSchema(questionnaire_name)
  output_path = os.path.join(args.download_dir, sanitizeFileName(questionnaire_name) + ".json")
  with open(output_path, 'w') as f_json:
    json.dump(schema, f_json, indent=2)
  if args.verbose:
    print("Saved {} to {}".format(questionnaire_name, output_path))

if args.verbose:
  print("Done")
