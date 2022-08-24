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
import json
import requests

JSON_FILEPATH = sys.argv[1]
SLACK_INCOMING_WEBHOOK_URL = os.environ['SLACK_INCOMING_WEBHOOK_URL']

with open(JSON_FILEPATH, 'r') as f_json:
	r = requests.post(SLACK_INCOMING_WEBHOOK_URL, json={'blocks': json.load(f_json)})
	if r.status_code == 200:
		print("OK")
	else:
		print("ERROR")
