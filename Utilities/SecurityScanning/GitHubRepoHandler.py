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
import time
import base64
import requests

class GitHubRepoHandler:
	def __init__(self, cli_args=None, bot_username="Bot", bot_email="user@localhost"):
		self.bot_username = bot_username
		self.bot_email = bot_email
		if (cli_args is None) or (cli_args.private_key is None):
			# Try to load it via environment variable
			if 'GITHUB_API_PRIVATE_KEY' not in os.environ:
				raise Exception("Error: --private_key was not specified and GITHUB_API_PRIVATE_KEY environment variable does not exist.")
			self.github_api_private_key = os.environ['GITHUB_API_PRIVATE_KEY']
		else:
			with open(cli_args.private_key, 'r') as f:
				self.github_api_private_key = f.read()

		if (cli_args is None) or (cli_args.app_id is None):
			# Try to set it via environment variable
			if 'GITHUB_API_APP_ID' not in os.environ:
				raise Exception("Error: --app_id was not specified and GITHUB_API_APP_ID environment variable does not exist.")
			self.app_id = os.environ['GITHUB_API_APP_ID']
		else:
			self.app_id = cli_args.app_id

		if (cli_args is None) or (cli_args.installation_id is None):
			# Try to set it via environment variable
			if 'GITHUB_API_INSTALLATION_ID' not in os.environ:
				raise Exception("Error: --installation_id was not specified and GITHUB_API_INSTALLATION_ID environment variable does not exist.")
			self.installation_id = os.environ['GITHUB_API_INSTALLATION_ID']
		else:
			self.installation_id = cli_args.installation_id

		if (cli_args is None) or (cli_args.repository is None):
			# Try to set it via environment variable
			if 'GITHUB_REPOSITORY' not in os.environ:
				raise Exception("Error: --repository was not specified and GITHUB_REPOSITORY environment variable does not exist.")
			self.repository = os.environ['GITHUB_REPOSITORY']
		else:
			self.repository = cli_args.repository

		payload = {}
		payload['iat'] = int(time.time())
		payload['exp'] = payload['iat'] + (60 * 10)
		payload['iss'] = self.app_id

		github_jwt = jwt.encode(payload, self.github_api_private_key, algorithm='RS256')

		application_headers = {}
		application_headers['Accept'] = 'application/vnd.github+json'
		application_headers['Authorization'] = 'Bearer ' + github_jwt.decode()

		installation_token_resp = requests.post("https://api.github.com/app/installations/" + self.installation_id + "/access_tokens", headers=application_headers)
		self.installation_token = installation_token_resp.json()['token']

	def updateGitHubFile(self, path, new_content_file):
		installation_headers = {}
		installation_headers['Accept'] = 'application/vnd.github+json'
		installation_headers['Authorization'] = 'token ' + self.installation_token

		prev_version_resp = requests.get("https://api.github.com/repos/" + self.repository + "/contents/" + path, headers=installation_headers)
		prev_version_sha = prev_version_resp.json()['sha']

		commit = {}
		commit['message'] = "Updating " + path
		commit['committer'] = {'name': self.bot_username, 'email': self.bot_email}
		with open(new_content_file, 'rb') as f:
			commit['content'] = base64.b64encode(f.read()).decode()
		commit['sha'] = prev_version_sha

		r = requests.put("https://api.github.com/repos/" + self.repository + "/contents/" + path, headers=installation_headers, json=commit)
		if r.status_code not in range(200, 300):
			raise Exception("Failed to update " + path)

	def downloadGitHubFile(self, path, save_path):
		installation_headers = {}
		installation_headers['Accept'] = 'application/vnd.github.raw'
		installation_headers['Authorization'] = 'token ' + self.installation_token

		resp = requests.get("https://api.github.com/repos/" + self.repository + "/contents/" + path, headers=installation_headers)
		if resp.status_code != 200:
			raise Exception("HTTP {} was returned when attempting to download {}.".format(resp.status_code, path))
		with open(save_path, 'wb') as f_save:
			f_save.write(resp.content)

	def readGitHubTextFile(self, path):
		installation_headers = {}
		installation_headers['Accept'] = 'application/vnd.github.raw'
		installation_headers['Authorization'] = 'token ' + self.installation_token

		resp = requests.get("https://api.github.com/repos/" + self.repository + "/contents/" + path, headers=installation_headers)
		if resp.status_code != 200:
			raise Exception("HTTP {} was returned when attempting to download {}.".format(resp.status_code, path))
		return resp.text

	def writeGitHubTextFile(self, path, text_content):
		installation_headers = {}
		installation_headers['Accept'] = 'application/vnd.github+json'
		installation_headers['Authorization'] = 'token ' + self.installation_token

		prev_version_resp = requests.get("https://api.github.com/repos/" + self.repository + "/contents/" + path, headers=installation_headers)
		prev_version_sha = prev_version_resp.json()['sha']

		commit = {}
		commit['message'] = "Updating " + path
		commit['committer'] = {'name': self.bot_username, 'email': self.bot_email}
		commit['content'] = base64.b64encode(text_content.encode()).decode()
		commit['sha'] = prev_version_sha

		r = requests.put("https://api.github.com/repos/" + self.repository + "/contents/" + path, headers=installation_headers, json=commit)
		if r.status_code not in range(200, 300):
			raise Exception("Failed to update " + path)

	def listGitHubDirectory(self, path):
		installation_headers = {}
		installation_headers['Accept'] = 'application/vnd.github+json'
		installation_headers['Authorization'] = 'token ' + self.installation_token

		resp = requests.get("https://api.github.com/repos/" + self.repository + "/contents/" + path, headers=installation_headers)
		if resp.status_code != 200:
			raise Exception("HTTP {} was returned when attempting to list {}.".format(resp.status_code, path))

		directory_contents = [ str(n['name']) for n in resp.json() ]
		return directory_contents
