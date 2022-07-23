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
import uuid
import argparse
import requests


CARDS_URL = "http://localhost:8080"
if "CARDS_URL" in os.environ:
  CARDS_URL = os.environ["CARDS_URL"].rstrip('/')

ADMIN_PASSWORD = "admin"
if "ADMIN_PASSWORD" in os.environ:
  ADMIN_PASSWORD = os.environ["ADMIN_PASSWORD"]


def create_subject_for_type(parent_subject_path, subject_identifier, subject_type_jcr_path):
  # Get the jcr:uuid for subject_type_jcr_path
  resp = requests.get(CARDS_URL + subject_type_jcr_path + ".json", auth=('admin', ADMIN_PASSWORD))
  if (resp.status_code != 200):
    raise Exception("ERROR: Could not get jcr:uuid for {}".format(subject_type_jcr_path))

  subject_type_jcr_uuid = resp.json()['jcr:uuid']

  creation_form_data = []
  creation_form_data.append(("jcr:primaryType", (None, "cards:Subject")))
  creation_form_data.append(("identifier", (None, subject_identifier)))
  creation_form_data.append(("type", (None, subject_type_jcr_uuid)))
  creation_form_data.append(("type@TypeHint", (None, "Reference")))

  new_subject_jcr_path = parent_subject_path + "/" + str(uuid.uuid4())
  resp = requests.post(CARDS_URL + new_subject_jcr_path, files=tuple(creation_form_data), auth=('admin', ADMIN_PASSWORD))

  if resp.status_code in range(200, 300):
    return new_subject_jcr_path
  else:
    raise Exception("ERROR: Cound not create {}".format(new_subject_jcr_path))


if __name__ == '__main__':
  argparser = argparse.ArgumentParser()
  argparser.add_argument('--parent', help='JCR node that this Subject node should be a child of', required=True)
  argparser.add_argument('--type_path', help='JCR path to the Subject Type of this Subject node', required=True)
  argparser.add_argument('--identifier', help='Subject identifier', required=True)
  args = argparser.parse_args()
  subject_jcr_path = create_subject_for_type(args.parent, args.identifier, args.type_path)
  print(subject_jcr_path)
