#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
"""

"""
This program reads STDIN line-by-line and generates a JSON file
listing the Maven packages, that were fed in via STDIN.
"""

import sys
import json

OUTPUT_JSON_FILE_PATH = sys.argv[1]

packages = []
for raw_line in sys.stdin.readlines():
	line = raw_line.rstrip()
	line = line[2:]
	if len(line.split('/')) < 3:
		continue
	groupId = '.'.join(line.split('/')[0:-3])
	artifactId = line.split('/')[-3]
	version = line.split('/')[-2]
	package = {'groupId': groupId, 'artifactId': artifactId, 'version': version}
	if package not in packages:
		packages.append(package)

with open(OUTPUT_JSON_FILE_PATH, 'w') as f_json:
	json.dump(packages, f_json, indent=2)

print("Processed {} Maven packages.".format(len(packages)))
