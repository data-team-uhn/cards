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
import distro
import argparse

import os_file_management
from GitHubRepoHandler import GitHubRepoHandler

argparser = argparse.ArgumentParser()
argparser.add_argument('--private_key', help='File path to the private key used for JWT signing')
argparser.add_argument('--app_id', help='GitHub App ID')
argparser.add_argument('--installation_id', help='GitHub App installation ID')
argparser.add_argument('--repository', help='OWNER/NAME of the GitHub repository (eg. data-team-uhn/cards)')
argparser.add_argument('--deployment_hostname', help='Directory name of the host (eg. cardsdemo.uhndata.io)')
args = argparser.parse_args()

THIS_DISTRO = distro.id()

gh_client = GitHubRepoHandler(cli_args=args, bot_username="github_publish_package_list.py")

try:
	for system_file_path in os_file_management.OS_FILE_SET[THIS_DISTRO]:
		system_file_name = os.path.basename(system_file_path)
		github_path = os.path.join('hosts', args.deployment_hostname, 'vm', system_file_name)
		gh_client.updateGitHubFile(github_path, system_file_path)
except KeyError:
	print("Error: Unable to handle this Linux distribution.")
	sys.exit(-1)
