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
import jwt
import time
import string
import argparse
import requests

argparser = argparse.ArgumentParser()
argparser.add_argument('--private_key', help='File path the to private key used for JWT signing')
argparser.add_argument('--app_id', help='GitHub App ID')
argparser.add_argument('--installation_id', help='GitHub App installation ID')
argparser.add_argument('--repository', help='OWNER/NAME of the GitHub repository (eg. data-team-uhn/cards)')
argparser.add_argument('--deployment_hostname', help='Directory name of the host (eg. cardsdemo.uhndata.io)')
argparser.add_argument('--download_dir', help='Directory to save the downloaded files to')
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

def isValidFilename(filename):
	validChars = string.ascii_letters + string.digits + '-' + '_' + ' '
	for c in filename:
		if c not in validChars:
			return False
	return True

def downloadFile(path, save_path, installation_token):
	installation_headers = {}
	installation_headers['Accept'] = 'application/vnd.github.raw'
	installation_headers['Authorization'] = 'token ' + installation_token

	resp = requests.get("https://api.github.com/repos/" + REPOSITORY + "/contents/" + path, headers=installation_headers)
	if resp.status_code != 200:
		raise Exception("HTTP {} was returned when attempting to download {}.".format(resp.status_code, path))
	with open(save_path, 'wb') as f_save:
		f_save.write(resp.content)

def listDirectory(path, installation_token):
	installation_headers = {}
	installation_headers['Accept'] = 'application/vnd.github+json'
	installation_headers['Authorization'] = 'token ' + installation_token

	resp = requests.get("https://api.github.com/repos/" + REPOSITORY + "/contents/" + path, headers=installation_headers)
	if resp.status_code != 200:
		raise Exception("HTTP {} was returned when attempting to list {}.".format(resp.status_code, path))

	directory_contents = [ str(n['name']) for n in resp.json() ]
	return directory_contents

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

# Create the {{args.download_dir}}/{{args.deployment_hostname}}/vm directory structure
os.mkdir(os.path.join(args.download_dir, args.deployment_hostname))
os.mkdir(os.path.join(args.download_dir, args.deployment_hostname, 'vm'))

# Use the installation_token to download everything in the repository under the args.deployment_hostname directory
for filename in listDirectory('hosts/' + args.deployment_hostname + '/vm', installation_token):
	if isValidFilename(filename):
		downloadFile('hosts/' + args.deployment_hostname + '/vm/' + filename, os.path.join(args.download_dir, args.deployment_hostname, 'vm', filename), installation_token)
