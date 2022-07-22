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
import json
import argparse
import requests

CARDS_URL = "http://localhost:8080"
if "CARDS_URL" in os.environ:
  CARDS_URL = os.environ["CARDS_URL"].rstrip('/')

ADMIN_PASSWORD = "admin"
if "ADMIN_PASSWORD" in os.environ:
  ADMIN_PASSWORD = os.environ["ADMIN_PASSWORD"]

def create_subject_type(subject_type_parent, subject_type_name):
  subject_type_properties = {}
  subject_type_properties["jcr:primaryType"] = "cards:SubjectType"
  subject_type_properties["label"] = subject_type_name
  subject_type_properties["cards:defaultOrder"] = 0
  subject_type_properties["subjectListLabel"] = ""

  creation_form_data = []
  creation_form_data.append((":contentType", (None, "json")))
  creation_form_data.append((":operation", (None, "import")))
  creation_form_data.append((":nameHint", (None, subject_type_name)))
  creation_form_data.append((":content", (None, json.dumps(subject_type_properties))))

  resp = requests.post(CARDS_URL + subject_type_parent, auth=('admin', ADMIN_PASSWORD), files=tuple(creation_form_data))
  if resp.status_code in range(200, 300):
    return
  else:
    raise Exception("Error occurred when creating {}/{}".format(subject_type_parent, subject_type_name))

if __name__ == '__main__':
  argparser = argparse.ArgumentParser()
  argparser.add_argument('--parent', help='JCR node that this Subject Type node should be a child of', required=True)
  argparser.add_argument('--name', help='Name of this Subject Type', required=True)
  args = argparser.parse_args()
  create_subject_type(args.parent, args.name)
  print("OK")
