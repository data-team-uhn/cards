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
import argparse
import requests
from requests.auth import HTTPBasicAuth

argparser = argparse.ArgumentParser()
argparser.add_argument('--bioportal_id', help='Install a vocabulary from BioPortal')
argparser.add_argument('--vocabulary_file', help='Install a vocabulary from a local file')
argparser.add_argument('--vocabulary_id', help='Identifier for local vocabulary file')
argparser.add_argument('--vocabulary_name', help='Name for local vocabulary file')
argparser.add_argument('--vocabulary_version', help='Version of local vocabulary file')
args = argparser.parse_args()

def checkInstallResponse(resp):
  if resp.status_code != 200:
    return False
  try:
    json_resp = json.loads(resp.text)
    if type(json_resp) != dict:
      return False
    if 'isSuccessful' not in json_resp:
      return False
    if json_resp['isSuccessful'] != True:
      return False
  except json.decoder.JSONDecodeError:
    return False
  return True

if args.bioportal_id is None and args.vocabulary_file is None:
  print("Must specify either --bioportal_id or --vocabulary_file")
  sys.exit(-1)

CARDS_URL = "http://localhost:8080"
if "CARDS_URL" in os.environ:
  CARDS_URL = os.environ["CARDS_URL"].rstrip('/')

ADMIN_PASSWORD = "admin"
if "ADMIN_PASSWORD" in os.environ:
  ADMIN_PASSWORD = os.environ["ADMIN_PASSWORD"]

if args.bioportal_id:
  print("Installing {} from BioPortal... ".format(args.bioportal_id), end='', flush=True)
  install_req = requests.post(CARDS_URL + "/Vocabularies?source=bioontology&identifier={}&overwrite=true".format(args.bioportal_id), auth=HTTPBasicAuth('admin', ADMIN_PASSWORD))
  if not checkInstallResponse(install_req):
    print("Install failed")
    sys.exit(-1)
  print("Installed")
  sys.exit()

if args.vocabulary_file:
  if None in [args.vocabulary_id, args.vocabulary_name, args.vocabulary_version]:
    print("Error: Must supply --vocabulary_id, --vocabulary_name, and --vocabulary_version arguments")
    sys.exit(-1)
  print("Installing {} from file:{}... ".format(args.vocabulary_id, args.vocabulary_file), end='', flush=True)
  form_data = {}
  form_data['identifier'] = args.vocabulary_id
  form_data['vocabName'] = args.vocabulary_name
  form_data['version'] = args.vocabulary_version
  with open(args.vocabulary_file, 'rb') as f_vocab:
    form_files = {}
    form_files['filename'] = f_vocab
    install_req = requests.post(CARDS_URL + "/Vocabularies?source=fileupload&overwrite=true", data=form_data, files=form_files, auth=HTTPBasicAuth('admin', ADMIN_PASSWORD))
    if not checkInstallResponse(install_req):
      print("Install failed")
      sys.exit(-1)
    print("Installed")
    sys.exit()
