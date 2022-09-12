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

import json
import argparse
from get_vulnerabilities_for_mvn_pkg import getVulnerabilities

argparser = argparse.ArgumentParser()
argparser.add_argument('--maven_package_list', help='Path to the JSON file listing the Maven packages to scan')
argparser.add_argument('--package_emoji', help='Software package icon [default: :java:]', default=':java:')
argparser.add_argument('--verbose', help='Verbose logging', action='store_true')
argparser.add_argument('--truncate_results', help='', type=int, default=-1)
argparser.add_argument('--markdown_report_file', help='Store the Markdown formatted list of all detected vulnerabilities (in order of decreasing severity) to this file')
args = argparser.parse_args()

with open(args.maven_package_list, 'r') as f_json:
	MAVEN_PACKAGE_LIST = json.load(f_json)

criticalServerity = []
highSeverity = []
mediumSeverity = []
lowSeverity = []

for mvnpkg in MAVEN_PACKAGE_LIST:
	if args.verbose:
		print("Processing {}...".format(mvnpkg))
	package_vulnerabilities = getVulnerabilities(mvnpkg['groupId'], mvnpkg['artifactId'], mvnpkg['version'])
	for vulnerability in package_vulnerabilities:
		# Sort vulnerabilities in order of severity: CRITICAL, HIGH, MEDIUM, LOW
		if vulnerability['Severity'] == "CRITICAL":
			selected_message_list = criticalServerity
		elif vulnerability['Severity'] == "HIGH":
			selected_message_list = highSeverity
		elif vulnerability['Severity'] == "MEDIUM":
			selected_message_list = mediumSeverity
		elif vulnerability['Severity'] == "LOW":
			selected_message_list = lowSeverity
		else:
			continue
		slack_message = args.package_emoji + "    *{}* - `{}:{}` is affected by _{}_    :warning:".format(vulnerability['Severity'], vulnerability['PkgName'], vulnerability['InstalledVersion'], vulnerability['VulnerabilityID'])
		md_report_message = args.package_emoji + "    **{}** - `{}:{}` is affected by _{}_    :warning:".format(vulnerability['Severity'], vulnerability['PkgName'], vulnerability['InstalledVersion'], vulnerability['VulnerabilityID'])
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
