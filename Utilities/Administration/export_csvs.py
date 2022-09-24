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
import argparse
import requests
from requests.auth import HTTPBasicAuth

argparser = argparse.ArgumentParser()
argparser.add_argument('--download_dir', help='Directory to where exported CSV files should be saved', required=True)
argparser.add_argument('--verbose', help='Enable verbose debug logging', action='store_true')
args = argparser.parse_args()

CARDS_URL = "http://localhost:8080"
if "CARDS_URL" in os.environ:
  CARDS_URL = os.environ["CARDS_URL"].rstrip('/')

ADMIN_PASSWORD = "admin"
if "ADMIN_PASSWORD" in os.environ:
  ADMIN_PASSWORD = os.environ["ADMIN_PASSWORD"]

def listQuestionnairePaths():
	questionnaires_resp = requests.get(CARDS_URL + "/Questionnaires.deep.json", auth=HTTPBasicAuth("admin", ADMIN_PASSWORD))
	if questionnaires_resp.status_code != 200:
		raise Exception("Invalid response from /Questionnaires.deep.json")
	questionnaires_resp_map = questionnaires_resp.json()
	questionnaire_paths = []
	for node_name in questionnaires_resp_map:
		node = questionnaires_resp_map[node_name]
		if type(node) != dict:
			continue
		if 'jcr:primaryType' in node:
			if node['jcr:primaryType'] == 'cards:Questionnaire':
				questionnaire_paths.append(node['@path'])
	return questionnaire_paths

# List the Questionnaires that are available in the CARDS instance
questionnaire_paths = listQuestionnairePaths()
for questionnaire_path in questionnaire_paths:
	export_file_path = os.path.join(args.download_dir, os.path.basename(questionnaire_path) + ".csv")
	if args.verbose:
		print("Exporting {} to {}".format(questionnaire_path, export_file_path), end='')
	csv_url = CARDS_URL + questionnaire_path + ".csv"
	csv_response = requests.get(csv_url, auth=HTTPBasicAuth("admin", ADMIN_PASSWORD))
	if csv_response.status_code != 200:
		if args.verbose:
			print(" [FAIL]")
		raise Exception("Invalid response while downloading the CSV for a Questionnaire")
	with open(export_file_path, 'wb') as f_export:
		f_export.write(csv_response.content)
	if args.verbose:
		print(" [DONE]")
