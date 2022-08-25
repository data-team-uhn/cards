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
import distro
import argparse
import requests

import os_file_management

argparser = argparse.ArgumentParser()
argparser.add_argument('--private_key', help='File path the to private key used for JWT signing')
argparser.add_argument('--app_id', help='GitHub App ID')
argparser.add_argument('--installation_id', help='GitHub App installation ID')
argparser.add_argument('--repository', help='OWNER/NAME of the GitHub repository (eg. data-team-uhn/cards)')
argparser.add_argument('--deployment_hostname', help='Directory name of the host (eg. cardsdemo.uhndata.io)')
args = argparser.parse_args()

THIS_DISTRO = distro.id()

with open(args.private_key, 'r') as f:
	PRIVATE_KEY = f.read()

def updateFile(path, new_content_file, installation_token):
	installation_headers = {}
	installation_headers['Accept'] = 'application/vnd.github+json'
	installation_headers['Authorization'] = 'token ' + installation_token

	prev_version_resp = requests.get("https://api.github.com/repos/" + args.repository + "/contents/" + path, headers=installation_headers)
	prev_version_sha = prev_version_resp.json()['sha']

	commit = {}
	commit['message'] = "Updating " + path
	commit['committer'] = {'name': 'update_os_package_list.py', 'email': 'user@localhost'}
	with open(new_content_file, 'rb') as f:
		commit['content'] = base64.b64encode(f.read()).decode()
	commit['sha'] = prev_version_sha

	r = requests.put("https://api.github.com/repos/" + args.repository + "/contents/" + path, headers=installation_headers, json=commit)
	if r.status_code not in range(200, 300):
		raise Exception("Failed to update " + path)

payload = {}
payload['iat'] = int(time.time())
payload['exp'] = payload['iat'] + (60 * 10)
payload['iss'] = args.app_id

github_jwt = jwt.encode(payload, PRIVATE_KEY, algorithm='RS256')

application_headers = {}
application_headers['Accept'] = 'application/vnd.github+json'
application_headers['Authorization'] = 'Bearer ' + github_jwt.decode()

installation_token_resp = requests.post("https://api.github.com/app/installations/" + args.installation_id + "/access_tokens", headers=application_headers)
installation_token = installation_token_resp.json()['token']

try:
	for system_file_path in os_file_management.OS_FILE_SET[THIS_DISTRO]:
		system_file_name = os.path.basename(system_file_path)
		github_path = os.path.join(args.deployment_hostname, 'vm', system_file_name)
		updateFile(github_path, system_file_path, installation_token)
except KeyError:
	print("Error: Unable to handle this Linux distribution.")
	sys.exit(-1)
