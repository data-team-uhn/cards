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

# Empirically determined (approximate) maximum width of text that can be
# placed in between the :package: (or alternative) and :warning: emojis
# plus the padding spaces without wrapping to a new line
SLACK_MESSAGE_MAX_WIDTH = 70

# Return the most verbose message possible describing a vulnerability
# without exceeding SLACK_MESSAGE_MAX_WIDTH
def getOptimalSlackMessage(pkgName, installedVersion, vulnerabilityID, severity):
	# Message templates in order of decreasing optimality
	message_templates = []
	message_templates.append("*{severity}* - `{pkgName} ({installedVersion})` is affected by _{vulnerabilityID}_")
	message_templates.append("*{severity}* - `{pkgName}` is affected by _{vulnerabilityID}_")
	message_templates.append("*{severity}* - _{vulnerabilityID}_")
	message_info = {}
	message_info['pkgName'] = pkgName
	message_info['installedVersion'] = installedVersion
	message_info['vulnerabilityID'] = vulnerabilityID
	message_info['severity'] = severity
	for message_template in message_templates:
		rendered_template = message_template.format(**message_info)
		if len(rendered_template) <= SLACK_MESSAGE_MAX_WIDTH:
			return rendered_template
	return message_templates[-1].format(**message_info)

# Returns a list containing only the unique elements from the input list
# while preserving the order. Eg.
# getUniqueElements([1,2,2,3,4,5,6,7,7,7,9]) => [1,2,3,4,5,6,7,9]
def getUniqueElements(input_list):
	output_list = []
	for element in input_list:
		if element not in output_list:
			output_list.append(element)
	return output_list

class TrivyToSlackConverter:
	def __init__(self, software_package_emoji=':package:'):
		self.software_package_emoji = software_package_emoji
		self.vulnerability_lists = {}
		self.vulnerability_lists['EOSL'] = []
		self.vulnerability_lists['CRITICAL'] = []
		self.vulnerability_lists['HIGH'] = []
		self.vulnerability_lists['MEDIUM'] = []
		self.vulnerability_lists['LOW'] = []

	def processVulnerabilities(self, detected_vulnerabilities, eosl=False):
		if eosl:
			self.vulnerability_lists['EOSL'].append((":no_entry:    Security support is no longer available.    :no_entry:", ":no_entry:    Security support is no longer available.    :no_entry:"))

		for vulnerabilityIndex in range(0, len(detected_vulnerabilities)):
			pkgName = detected_vulnerabilities[vulnerabilityIndex]['PkgName']
			installedVersion = detected_vulnerabilities[vulnerabilityIndex]['InstalledVersion']
			vulnerabilityID = detected_vulnerabilities[vulnerabilityIndex]['VulnerabilityID']
			severity = detected_vulnerabilities[vulnerabilityIndex]['Severity']

			if severity not in self.vulnerability_lists:
				continue

			selected_message_list = self.vulnerability_lists[severity]
			slack_message = self.software_package_emoji + "    " + getOptimalSlackMessage(pkgName, installedVersion, vulnerabilityID, severity) + "    :warning:"
			md_report_message = self.software_package_emoji + "    **{}** - `{} ({})` is affected by _{}_    :warning:".format(severity, pkgName, installedVersion, vulnerabilityID)
			selected_message_list.append((slack_message, md_report_message))

	def getSortedVulnerabilityList(self):
		ordered_vulnerabilities = []
		for severity in ['EOSL', 'CRITICAL', 'HIGH', 'MEDIUM', 'LOW']:
			ordered_vulnerabilities += self.vulnerability_lists[severity]
		return ordered_vulnerabilities

	def getSlackMessages(self, truncate=-1):
		slackMessages = [v[0] for v in self.getSortedVulnerabilityList()]
		slackMessages = getUniqueElements(slackMessages)
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
		if len(self.getSortedVulnerabilityList()) > 0:
			return [v[1] for v in self.getSortedVulnerabilityList()]
		else:
			return [":heavy_check_mark:    No vulnerabilities detected!    :heavy_check_mark:"]

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

	# Were there detected vulnerabilities?
	if 'Vulnerabilities' in trivy_report['Results'][0]:
		detected_vulnerabilities = trivy_report['Results'][0]['Vulnerabilities']
	else:
		detected_vulnerabilities = []

	# Is this OS no longer getting security updates?
	eosl = False
	if 'Metadata' in trivy_report:
		if 'OS' in trivy_report['Metadata']:
			if 'EOSL' in trivy_report['Metadata']['OS']:
				eosl = trivy_report['Metadata']['OS']['EOSL']

	trivy_to_slack_converter = TrivyToSlackConverter(software_package_emoji=args.package_emoji)
	trivy_to_slack_converter.processVulnerabilities(detected_vulnerabilities, eosl=eosl)

	if args.markdown_report_file:
		with open(args.markdown_report_file, 'w') as f_markdown_report:
			f_markdown_report.write('\n\n'.join(trivy_to_slack_converter.getMarkdownReportMessages()))

	print(json.dumps(trivy_to_slack_converter.getSlackBlock(truncate=args.truncate_results)))
