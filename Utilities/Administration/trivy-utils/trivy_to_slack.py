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
argparser.add_argument('--truncate_results', help='Truncate the list of vulnerabilities to this length', type=int, default=-1)
argparser.add_argument('--markdown_report_file', help='Store the Markdown formatted list of all detected vulnerabilities (in order of decreasing severity) to this file')
args = argparser.parse_args()

SOFTWARE_PACKAGE_EMOJI = args.package_emoji

trivy_report = json.load(sys.stdin)
detected_vulnerabilities = trivy_report['Results'][0]['Vulnerabilities']

criticalServerity = []
highSeverity = []
mediumSeverity = []
lowSeverity = []

for vulnerabilityIndex in range(0, len(detected_vulnerabilities)):
	pkgName = detected_vulnerabilities[vulnerabilityIndex]['PkgName']
	installedVersion = detected_vulnerabilities[vulnerabilityIndex]['InstalledVersion']
	vulnerabilityID = detected_vulnerabilities[vulnerabilityIndex]['VulnerabilityID']
	severity = detected_vulnerabilities[vulnerabilityIndex]['Severity']

	# Sort vulnerabilities in order of severity: CRITICAL, HIGH, MEDIUM, LOW
	if severity == "CRITICAL":
		selected_message_list = criticalServerity
	elif severity == "HIGH":
		selected_message_list = highSeverity
	elif severity == "MEDIUM":
		selected_message_list = mediumSeverity
	elif severity == "LOW":
		selected_message_list = lowSeverity
	else:
		continue

	slack_message = SOFTWARE_PACKAGE_EMOJI + "    *{}* - `{}` is affected by _{}_    :warning:".format(severity, pkgName, vulnerabilityID)
	md_report_message = SOFTWARE_PACKAGE_EMOJI + "    **{}** - `{}` is affected by _{}_    :warning:".format(severity, pkgName, vulnerabilityID)
	selected_message_list.append((slack_message, md_report_message))

ordered_vulnerabilities = criticalServerity + highSeverity + mediumSeverity + lowSeverity
slackMessages = [v[0] for v in ordered_vulnerabilities]
mdReportMessages = [v[1] for v in ordered_vulnerabilities]

if args.truncate_results >= 0:
	total_slack_messages = len(slackMessages)
	slackMessages = slackMessages[0:args.truncate_results]
	if total_slack_messages > args.truncate_results:
		slackMessages.append("    ... and {} more".format(total_slack_messages - args.truncate_results))

if args.markdown_report_file:
	with open(args.markdown_report_file, 'w') as f_markdown_report:
		f_markdown_report.write('\n\n'.join(mdReportMessages))

slack_block = {}
slack_block['type'] = 'section'
slack_block['text'] = {}
slack_block['text']['type'] = 'mrkdwn'

if len(slackMessages) > 0:
	slack_block['text']['text'] = '\n'.join(slackMessages)
else:
	slack_block['text']['text'] = ":white_check_mark:    No vulnerabilities detected!    :white_check_mark:"

print(json.dumps(slack_block))
