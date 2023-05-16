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
import json
import docker

POM_XML_TEMPLATE = """
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>com.mycompany.app</groupId>
  <artifactId>my-app</artifactId>
  <version>1.0-SNAPSHOT</version>

  <dependencies>
    <dependency>
      <groupId>{MAVEN_ARTIFACT_GROUP_ID}</groupId>
      <artifactId>{MAVEN_ARTIFACT_ARTIFACT_ID}</artifactId>
      <version>{MAVEN_ARTIFACT_VERSION}</version>
    </dependency>
  </dependencies>
</project>
"""

DOCKER_CLIENT = docker.from_env()

def getVulnerabilities(group, artifact, version):
	with open("pom.xml", 'w') as f_pom:
		f_pom.write(POM_XML_TEMPLATE.format(MAVEN_ARTIFACT_GROUP_ID=group, MAVEN_ARTIFACT_ARTIFACT_ID=artifact, MAVEN_ARTIFACT_VERSION=version))

	VOLUME_MOUNTS = []
	VOLUME_MOUNTS.append(os.path.expanduser("~/trivy-cache") + ":/root/.cache")
	VOLUME_MOUNTS.append(os.path.realpath("pom.xml") + ":/my-app/pom.xml")

	vulnerabilities_result = DOCKER_CLIENT.containers.run('aquasec/trivy', 'fs --security-checks vuln --ignore-unfixed --format json /my-app', auto_remove=True, network="none", volumes=VOLUME_MOUNTS)
	try:
		detected_vulnerabilities = json.loads(vulnerabilities_result.decode())['Results'][0]['Vulnerabilities']
	except KeyError:
		detected_vulnerabilities = []
	vulnerabilities_list = []
	for vulnerabilityIndex in range(0, len(detected_vulnerabilities)):
		this_vuln = {}
		this_vuln['PkgName'] = detected_vulnerabilities[vulnerabilityIndex]['PkgName']
		this_vuln['InstalledVersion'] = detected_vulnerabilities[vulnerabilityIndex]['InstalledVersion']
		this_vuln['VulnerabilityID'] = detected_vulnerabilities[vulnerabilityIndex]['VulnerabilityID']
		this_vuln['Severity'] = detected_vulnerabilities[vulnerabilityIndex]['Severity']
		vulnerabilities_list.append(this_vuln)

	os.remove(os.path.realpath("pom.xml"))

	return vulnerabilities_list

if __name__ == '__main__':
	import argparse

	argparser = argparse.ArgumentParser()
	argparser.add_argument('--group_id')
	argparser.add_argument('--artifact_id')
	argparser.add_argument('--version')
	args = argparser.parse_args()

	vulns = getVulnerabilities(args.group_id, args.artifact_id, args.version)
	print(vulns)
