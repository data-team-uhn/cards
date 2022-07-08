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
import json
import hashlib
import argparse

argparser = argparse.ArgumentParser()
argparser.add_argument('--backup_directory', help='Path to backup directory', type=str, required=True)
argparser.add_argument('--form_list_file', help='Path to the Form list file', type=str, required=True)
argparser.add_argument('--subject_list_file', help='Path to the Subject list file', type=str, required=True)
args = argparser.parse_args()

BACKUP_DIRECTORY = args.backup_directory
FORM_LIST_FILE = args.form_list_file
SUBJECT_LIST_FILE = args.subject_list_file

def calculateFileSha256(filepath):
  h = hashlib.sha256()
  with open(filepath, 'rb') as f:
    h.update(f.read())
  return h.hexdigest()

with open(FORM_LIST_FILE, 'r') as f:
  FORM_LIST = json.loads(f.read())

with open(SUBJECT_LIST_FILE, 'r') as f:
  SUBJECT_LIST = json.loads(f.read())

for form in FORM_LIST:
  form_path = form[0]
  form_last_modified = form[1]
  form_uuid_name = form_path.split('/')[-1]

  # Do we have this Form backed up in JSON format?
  # That is - do we have a Form with an @path of $form_path and
  # a jcr:lastModified value of $form_last_modified

  json_path = os.path.join(BACKUP_DIRECTORY, "Forms", form_uuid_name + ".json")
  if not os.path.isfile(json_path):
    print("ERROR: The backup is incomplete due to missing file: {}.".format(json_path))
    sys.exit(-1)
  with open(json_path, 'r') as f_json:
    form_data = json.load(f_json)
  if form_path != form_data['@path']:
    print("ERROR: The backup is incomplete due to a missing {} JCR node.".format(form_path))
    sys.exit(-1)
  if form_last_modified != form_data['jcr:lastModified']:
    print("ERROR: The backup is incomplete due to an invalid jcr:lastModified property in {}.".format(form_path))
    sys.exit(-1)

  # If this Form JSON file makes reference to any binary files (blobs), do we have them?
  for response_path in form_data["responses"]:
    response = form_data["responses"][response_path]
    if "fileDataSha256" in response:
      for filename in response["fileDataSha256"]:
        filehash = response["fileDataSha256"][filename]
        blobpath = os.path.join(BACKUP_DIRECTORY, "blobs", filehash + ".blob")
        if not os.path.isfile(blobpath):
          print("ERROR: The backup is incomplete due to missing file: {}".format(blobpath))
          sys.exit(-1)

        # Verify the integrity of this file
        if filehash != calculateFileSha256(blobpath):
          print("ERROR: The backup is incomplete because {} is corrupt.".format(blobpath))
          sys.exit(-1)

for subject in SUBJECT_LIST:
  subject_path = subject[0]
  subject_last_modified = subject[1]
  subject_uuid_name = subject_path.split('/')[-1]

  # Do we have this Subject backed up in JSON format?
  # We can ignore subject_last_modified as Subjects are only created
  # and deleted. They are never modified.

  json_path = os.path.join(BACKUP_DIRECTORY, "Subjects", subject_uuid_name + ".json")
  if not os.path.isfile(json_path):
    print("ERROR: The backup is incomplete due to missing file: {}.".format(json_path))
    sys.exit(-1)
  with open(json_path, 'r') as f_json:
    subject_data = json.load(f_json)
  if subject_path != subject_data["@path"]:
    print("ERROR: The backup is incomplete due to a missing {} JCR node.".format(subject_path))
    sys.exit(-1)

print("OK: Backup is valid.")
