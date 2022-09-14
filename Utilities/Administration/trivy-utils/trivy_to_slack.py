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

class TrivyToSlackConverter:
	def __init__(self, software_package_emoji=':package:'):
		self.software_package_emoji = software_package_emoji
		self.vulnerability_lists = {}
		self.vulnerability_lists['CRITICAL'] = []
		self.vulnerability_lists['HIGH'] = []
		self.vulnerability_lists['MEDIUM'] = []
		self.vulnerability_lists['LOW'] = []

	def processVulnerabilities(self, detected_vulnerabilities):
		for vulnerabilityIndex in range(0, len(detected_vulnerabilities)):
			pkgName = detected_vulnerabilities[vulnerabilityIndex]['PkgName']
			installedVersion = detected_vulnerabilities[vulnerabilityIndex]['InstalledVersion']
			vulnerabilityID = detected_vulnerabilities[vulnerabilityIndex]['VulnerabilityID']
			severity = detected_vulnerabilities[vulnerabilityIndex]['Severity']

			if severity not in self.vulnerability_lists:
				continue

			selected_message_list = self.vulnerability_lists[severity]
			slack_message = self.software_package_emoji + "    *{}* - `{}` is affected by _{}_    :warning:".format(severity, pkgName, vulnerabilityID)
			md_report_message = self.software_package_emoji + "    **{}** - `{}` is affected by _{}_    :warning:".format(severity, pkgName, vulnerabilityID)
			selected_message_list.append((slack_message, md_report_message))

	def getSortedVulnerabilityList(self):
		ordered_vulnerabilities = []
		for severity in ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']:
			ordered_vulnerabilities += self.vulnerability_lists[severity]
		return ordered_vulnerabilities

	def getSlackMessages(self, truncate=-1):
		slackMessages = [v[0] for v in self.getSortedVulnerabilityList()]
		if truncate >= 0:
			original_length = len(slackMessages)
			slackMessages = slackMessages[0:truncate]
			if original_length > truncate:
				slackMessages.append("    ... and {} more".format(original_length - truncate))
		return slackMessages

	def getSlackBlock(self, truncate=-1):
		slack_block = {}
		slack_block['type'] = 'section'
		slack_block['text'] = {}
		slack_block['text']['type'] = 'mrkdwn'

		slackMessages = self.getSlackMessages(truncate)
		if len(slackMessages) > 0:
			slack_block['text']['text'] = '\n'.join(slackMessages)
		else:
			slack_block['text']['text'] = ":white_check_mark:    No vulnerabilities detected!    :white_check_mark:"
		return slack_block

	def getMarkdownReportMessages(self):
		return [v[1] for v in self.getSortedVulnerabilityList()]

if __name__ == '__main__':
	import sys
	import json
	import argparse

	argparser = argparse.ArgumentParser()
	argparser.add_argument('--package_emoji', help='Software package icon [default: :package:]', default=':package:')
	argparser.add_argument('--truncate_results', help='Truncate the list of vulnerabilities to this length', type=int, default=-1)
	argparser.add_argument('--markdown_report_file', help='Store the Markdown formatted list of all detected vulnerabilities (in order of decreasing severity) to this file')
	args = argparser.parse_args()

	trivy_report = json.load(sys.stdin)
	detected_vulnerabilities = trivy_report['Results'][0]['Vulnerabilities']
	trivy_to_slack_converter = TrivyToSlackConverter(software_package_emoji=args.package_emoji)
	trivy_to_slack_converter.processVulnerabilities(detected_vulnerabilities)

	if args.markdown_report_file:
		with open(args.markdown_report_file, 'w') as f_markdown_report:
			f_markdown_report.write('\n\n'.join(trivy_to_slack_converter.getMarkdownReportMessages()))

	print(json.dumps(trivy_to_slack_converter.getSlackBlock(truncate=args.truncate_results)))
