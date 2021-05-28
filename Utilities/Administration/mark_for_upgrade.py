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
import re
import requests
from requests.auth import HTTPBasicAuth

CARDS_URL = os.environ['CARDS_URL']
SLING_GITHUB_PASSWORD = os.environ['SLING_GITHUB_PASSWORD']
LATEST_GIT_TAG = os.environ['LATEST_GIT_TAG']
GITHUB_REF = os.environ['GITHUB_REF']

"""
Returns True if the version specified by `a` is greater than or equal to
the version specified by `b`.
"""
def isNewerOrEqualVersion(a, b):
  return bool([int(u) for u in a.split('.')] >= [int(u) for u in b.split('.')])

print("Latest tagged release in git is: {}".format(LATEST_GIT_TAG))
print("GitHub REF for this execution is: {}".format(GITHUB_REF))

# Check if GITHUB_REF >= LATEST_GIT_TAG
matcher = re.compile('refs/tags/cards-((\\d+\\.)*\\d+)$')
match = matcher.match(GITHUB_REF)
if match is not None:
  version_string = match.groups()[0]
  if isNewerOrEqualVersion(version_string, LATEST_GIT_TAG):
    requests.post(CARDS_URL + "/UpgradeMarker", data={'value': True, 'upgradeVersion': version_string}, auth=HTTPBasicAuth('github', SLING_GITHUB_PASSWORD))
  else:
    print("Not the latest release!")
else:
  print("Not a valid comparable release tag!")

resp = requests.get(CARDS_URL + "/UpgradeMarker.json", auth=HTTPBasicAuth('github', SLING_GITHUB_PASSWORD))
print(resp.json())
