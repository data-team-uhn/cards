#!/bin/bash

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

DEPLOYMENT_HOSTNAME=$1
SLACK_MESSAGES_FILE=$2

# The following environment variables should be configured:
# - GITHUB_API_PRIVATE_KEY
# - GITHUB_API_APP_ID
# - GITHUB_API_INSTALLATION_ID
# - GITHUB_REPOSITORY

# Download the NPM/Yarn package configuration data for the deployment from GitHub
PACKAGE_CONF_DIR=$(mktemp -d -p .)
python3 ../github_download_yarn_packages_config.py --deployment_hostname $DEPLOYMENT_HOSTNAME --download_dir $PACKAGE_CONF_DIR || exit -1

# Scan with Trivy and format the results to a Slack message
docker run --rm -v $(realpath $PACKAGE_CONF_DIR)/$DEPLOYMENT_HOSTNAME/docker/cards/yarn.lock:/yarn.lock aquasec/trivy fs --security-checks vuln --ignore-unfixed /yarn.lock --format json | python3 trivy_to_slack.py --package_emoji :octocat: > $SLACK_MESSAGES_FILE

rm -rf $PACKAGE_CONF_DIR
