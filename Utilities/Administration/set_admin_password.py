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
import argparse
import requests
from requests.auth import HTTPBasicAuth

argparser = argparse.ArgumentParser()
argparser.add_argument('--user', help='The user to set the password for', default='admin')
args = argparser.parse_args()

CARDS_URL = "http://localhost:8080"
if "CARDS_URL" in os.environ:
  CARDS_URL = os.environ["CARDS_URL"].rstrip('/')

ADMIN_PASSWORD = "admin"
if "ADMIN_PASSWORD" in os.environ:
  ADMIN_PASSWORD = os.environ["ADMIN_PASSWORD"]

new_password = input("New {} password: ".format(args.user))
form_data = {}
form_data['newPwd'] = new_password
form_data['newPwdConfirm'] = new_password
if args.user == "admin":
  form_data['oldPwd'] = ADMIN_PASSWORD

pw_change_url = CARDS_URL + "/system/userManager/user/{}.changePassword.html".format(args.user)
change_req = requests.post(pw_change_url, data=form_data, auth=HTTPBasicAuth('admin', ADMIN_PASSWORD))
if change_req.status_code != 200:
  print("Error while setting {} password".format(args.user))
  sys.exit(-1)
print("Set {} password".format(args.user))
