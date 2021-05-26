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
import string
import argparse
import requests
from requests.auth import HTTPBasicAuth

VALID_VERSION_CHARS = string.digits + "."

def validate_version_string(version_string):
  for c in version_string:
    if c not in VALID_VERSION_CHARS:
      return False
  return True

argparser = argparse.ArgumentParser()
argparser.add_argument('--cards_url', help='URL for the running CARDS instance')
args = argparser.parse_args()

CARDS_URL = "http://localhost:8080"
if "CARDS_URL" in os.environ:
  CARDS_URL = os.environ["CARDS_URL"].rstrip('/')
if args.cards_url is not None:
  CARDS_URL = args.cards_url.rstrip('/')

SLING_GITHUB_PASSWORD = "github"
if "SLING_GITHUB_PASSWORD" in os.environ:
  SLING_GITHUB_PASSWORD = os.environ["SLING_GITHUB_PASSWORD"]

upgrade_check_req = requests.get(CARDS_URL + "/UpgradeMarker.json", auth=HTTPBasicAuth('github', SLING_GITHUB_PASSWORD))
upgrade_marker_value = upgrade_check_req.json()['value']
if type(upgrade_marker_value) == bool:
  if upgrade_marker_value == False:
    sys.exit(1)
  upgrade_marker_version = upgrade_check_req.json()['upgradeVersion']
  if type(upgrade_marker_version) == str:
    if validate_version_string(upgrade_marker_version):
      print(upgrade_marker_version)
      sys.exit(0)

sys.exit(-1)
