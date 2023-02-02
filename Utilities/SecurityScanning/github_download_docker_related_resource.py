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

from GitHubRepoHandler import GitHubRepoHandler

argparser = argparse.ArgumentParser()
argparser.add_argument('--private_key', help='File path to the private key used for JWT signing')
argparser.add_argument('--app_id', help='GitHub App ID')
argparser.add_argument('--installation_id', help='GitHub App installation ID')
argparser.add_argument('--repository', help='OWNER/NAME of the GitHub repository (eg. data-team-uhn/cards)')
argparser.add_argument('--deployment_hostname', help='Directory name of the host (eg. cardsdemo.uhndata.io)')
argparser.add_argument('--resource_name', help='Name of the resource to download (eg. maven.json)')
argparser.add_argument('--download_dir', help='Directory to save the downloaded file to')
args = argparser.parse_args()

gh_client = GitHubRepoHandler(cli_args=args, bot_username="github_download_docker_related_resource.py")

# Get the Docker image hash ID that is being used in this deployment
cards_image_hash = gh_client.readGitHubTextFile('hosts/' + args.deployment_hostname + '/docker/cards').rstrip()

cards_image_hash_type = cards_image_hash.split(':')[0]
cards_image_hash_value = cards_image_hash.split(':')[1]

if cards_image_hash_type != 'sha256':
	print("ERROR: Docker image hash type not supported!")
	exit(-1)

if not cards_image_hash_value.isalnum():
	print("ERROR: Hash value must be alpha-numeric!")
	exit(-1)

# Create the {{args.download_dir}}/{{args.deployment_hostname}}/docker/cards directory structure
os.mkdir(os.path.join(args.download_dir, args.deployment_hostname))
os.mkdir(os.path.join(args.download_dir, args.deployment_hostname, 'docker'))
os.mkdir(os.path.join(args.download_dir, args.deployment_hostname, 'docker', 'cards'))

# Get the resource file associated with the hash ID
gh_client.downloadGitHubFile('docker/' + cards_image_hash_value + '/' + args.resource_name, os.path.join(args.download_dir, args.deployment_hostname, 'docker', 'cards', args.resource_name))
