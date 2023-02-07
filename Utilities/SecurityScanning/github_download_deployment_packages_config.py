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
import string
import argparse

from GitHubRepoHandler import GitHubRepoHandler

argparser = argparse.ArgumentParser()
argparser.add_argument('--private_key', help='File path to the private key used for JWT signing')
argparser.add_argument('--app_id', help='GitHub App ID')
argparser.add_argument('--installation_id', help='GitHub App installation ID')
argparser.add_argument('--repository', help='OWNER/NAME of the GitHub repository (eg. data-team-uhn/cards)')
argparser.add_argument('--deployment_hostname', help='Directory name of the host (eg. cardsdemo.uhndata.io)')
argparser.add_argument('--download_dir', help='Directory to save the downloaded files to')
args = argparser.parse_args()

gh_client = GitHubRepoHandler(cli_args=args, bot_username="github_download_deployment_packages_config.py")

def isValidFilename(filename):
	validChars = string.ascii_letters + string.digits + '-' + '_' + ' '
	for c in filename:
		if c not in validChars:
			return False
	return True

# Create the {{args.download_dir}}/{{args.deployment_hostname}}/vm directory structure
os.mkdir(os.path.join(args.download_dir, args.deployment_hostname))
os.mkdir(os.path.join(args.download_dir, args.deployment_hostname, 'vm'))

# Use the installation_token to download everything in the repository under the args.deployment_hostname directory
for filename in gh_client.listGitHubDirectory('hosts/' + args.deployment_hostname + '/vm'):
	if isValidFilename(filename):
		gh_client.downloadGitHubFile('hosts/' + args.deployment_hostname + '/vm/' + filename, os.path.join(args.download_dir, args.deployment_hostname, 'vm', filename))
