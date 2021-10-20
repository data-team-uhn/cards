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

import os
import sys
import requests
from requests.auth import HTTPBasicAuth

CARDS_URL = "http://localhost:8080"
if "CARDS_URL" in os.environ:
  CARDS_URL = os.environ["CARDS_URL"].rstrip('/')

ADMIN_PASSWORD = "admin"
if "ADMIN_PASSWORD" in os.environ:
  ADMIN_PASSWORD = os.environ["ADMIN_PASSWORD"]

json_data = {}
json_data['allow'] = True
json_data['path'] = '/home'
json_data['principal'] = 'saml2-user-mgt'
json_data['privileges'] = ["jcr:all"]
json_data['restrictions'] = []

r = requests.put(CARDS_URL + "/bin/cpm/nodes/security.accessPolicy.json/home", json=json_data, auth=HTTPBasicAuth('admin', ADMIN_PASSWORD))

if (r.status_code != 200):
  print("Error while granting jcr:all permissions to the /home node")
  sys.exit(-1)
print("Granted jcr:all permissions to the /home node")
