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

import sys
import json
import argparse

argparser = argparse.ArgumentParser()
argparser.add_argument('--package_emoji', help='Software package icon [default: :package:]', default=':package:')
args = argparser.parse_args()

SOFTWARE_PACKAGE_EMOJI = args.package_emoji

trivy_report = json.load(sys.stdin)
detected_vulnerabilities = trivy_report['Results'][0]['Vulnerabilities']
slackMessages = []

for vulnerabilityIndex in range(0, len(detected_vulnerabilities)):
	pkgName = detected_vulnerabilities[vulnerabilityIndex]['PkgName']
	installedVersion = detected_vulnerabilities[vulnerabilityIndex]['InstalledVersion']
	vulnerabilityID = detected_vulnerabilities[vulnerabilityIndex]['VulnerabilityID']
	severity = detected_vulnerabilities[vulnerabilityIndex]['Severity']
	slackMessages.append(SOFTWARE_PACKAGE_EMOJI + "    *{}* - `{}` is affected by _{}_    :warning:".format(severity, pkgName, vulnerabilityID))

slack_block = {}
slack_block['type'] = 'section'
slack_block['text'] = {}
slack_block['text']['type'] = 'mrkdwn'

if len(slackMessages) > 0:
	slack_block['text']['text'] = '\n'.join(slackMessages)
else:
	slack_block['text']['text'] = ":white_check_mark:    No vulnerabilities detected!    :white_check_mark:"

print(json.dumps(slack_block))
