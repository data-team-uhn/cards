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
import jwt
import sys
import time
import base64
import argparse
import requests

argparser = argparse.ArgumentParser()
argparser.add_argument('--private_key', help='File path the to private key used for JWT signing')
argparser.add_argument('--app_id', help='GitHub App ID')
argparser.add_argument('--installation_id', help='GitHub App installation ID')
argparser.add_argument('--repository', help='OWNER/NAME of the GitHub repository (eg. data-team-uhn/cards)')
argparser.add_argument('--deployment_hostname', help='Directory name of the host (eg. cardsdemo.uhndata.io)')
argparser.add_argument('--markdown_report_file', help='Path to the Markdown-formatted security scan report to be uploaded')
args = argparser.parse_args()

if args.private_key is None:
	# Try to load it via environment variable
	if 'GITHUB_API_PRIVATE_KEY' not in os.environ:
		print("Error: --private_key was not specified and GITHUB_API_PRIVATE_KEY environment variable does not exist.")
		sys.exit(-1)
	PRIVATE_KEY = os.environ['GITHUB_API_PRIVATE_KEY']
else:
	with open(args.private_key, 'r') as f:
		PRIVATE_KEY = f.read()

if args.app_id is None:
	# Try to set it via environment variable
	if 'GITHUB_API_APP_ID' not in os.environ:
		print("Error: --app_id was not specified and GITHUB_API_APP_ID environment variable does not exist.")
		sys.exit(-1)
	APP_ID = os.environ['GITHUB_API_APP_ID']
else:
	APP_ID = args.app_id

if args.installation_id is None:
	# Try to set it via environment variable
	if 'GITHUB_API_INSTALLATION_ID' not in os.environ:
		print("Error: --installation_id was not specified and GITHUB_API_INSTALLATION_ID environment variable does not exist.")
		sys.exit(-1)
	INSTALLATION_ID = os.environ['GITHUB_API_INSTALLATION_ID']
else:
	INSTALLATION_ID = args.installation_id

if args.repository is None:
	# Try to set it via environment variable
	if 'GITHUB_REPOSITORY' not in os.environ:
		print("Error: --repository was not specified and GITHUB_REPOSITORY environment variable does not exist.")
		sys.exit(-1)
	REPOSITORY = os.environ['GITHUB_REPOSITORY']
else:
	REPOSITORY = args.repository

def updateFile(path, new_content_file, installation_token):
	installation_headers = {}
	installation_headers['Accept'] = 'application/vnd.github+json'
	installation_headers['Authorization'] = 'token ' + installation_token

	prev_version_resp = requests.get("https://api.github.com/repos/" + REPOSITORY + "/contents/" + path, headers=installation_headers)
	prev_version_sha = prev_version_resp.json()['sha']

	commit = {}
	commit['message'] = "Updating " + path
	commit['committer'] = {'name': 'github_publish_scan_report.py', 'email': 'user@localhost'}
	with open(new_content_file, 'rb') as f:
		commit['content'] = base64.b64encode(f.read()).decode()
	commit['sha'] = prev_version_sha

	r = requests.put("https://api.github.com/repos/" + REPOSITORY + "/contents/" + path, headers=installation_headers, json=commit)
	if r.status_code not in range(200, 300):
		raise Exception("Failed to update " + path)

payload = {}
payload['iat'] = int(time.time())
payload['exp'] = payload['iat'] + (60 * 10)
payload['iss'] = APP_ID

github_jwt = jwt.encode(payload, PRIVATE_KEY, algorithm='RS256')

application_headers = {}
application_headers['Accept'] = 'application/vnd.github+json'
application_headers['Authorization'] = 'Bearer ' + github_jwt.decode()

installation_token_resp = requests.post("https://api.github.com/app/installations/" + INSTALLATION_ID + "/access_tokens", headers=application_headers)
installation_token = installation_token_resp.json()['token']

github_path = os.path.join('reports', args.deployment_hostname + '.md')
updateFile(github_path, args.markdown_report_file, installation_token)
