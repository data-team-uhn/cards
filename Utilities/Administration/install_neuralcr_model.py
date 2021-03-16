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

SUPPORTED_MODEL_TYPES = ["neural", "basic"]

argparser = argparse.ArgumentParser()
argparser.add_argument('--model_name', help='Make model available under this name')
argparser.add_argument('--model_type', help='Specify the model type; neural or basic')
argparser.add_argument('--param_dir', help='(NeuralCR Models): directory in neuralcr container containing ncr_weights.h5, config.json, onto.json')
argparser.add_argument('--fasttext', help='(NeuralCR Models): fasttext word vector file in neuralcr container')
argparser.add_argument('--threshold', help='Model confidence threshold')
argparser.add_argument('--id_file', help='(BasicCR Models): JSON file in neuralcr container mapping synonymous names to a common identifier')
argparser.add_argument('--title_file', help='(BasicCR Models): JSON file in neuralcr container mapping concept identifiers to their human-readable names')
args = argparser.parse_args()

CARDS_URL = "http://localhost:8080"
if "CARDS_URL" in os.environ:
  CARDS_URL = os.environ["CARDS_URL"].rstrip('/')

ADMIN_PASSWORD = "admin"
if "ADMIN_PASSWORD" in os.environ:
  ADMIN_PASSWORD = os.environ["ADMIN_PASSWORD"]

if None in [args.model_name, args.model_type]:
  print("Must specify a --model_name and a --model_type")
  sys.exit(-1)

if args.model_type not in SUPPORTED_MODEL_TYPES:
  print("Unsupported model type")
  sys.exit(-1)

if args.model_type == "neural":
  if None in [args.param_dir, args.fasttext, args.threshold]:
    print("Must specify --param_dir, --fasttext, --threshold")
    sys.exit(-1)
  install_request = {}
  install_request['model_type'] = "neural"
  install_request['param_dir'] = str(args.param_dir)
  install_request['word_model_file'] = str(args.fasttext)
  install_request['threshold'] = float(args.threshold)

if args.model_type == "basic":
  if None in [args.id_file, args.title_file]:
    print("Must specify --id_file, --title_file")
    sys.exit(-1)
  install_request = {}
  install_request['model_type'] = "basic"
  install_request['id_file'] = str(args.id_file)
  install_request['title_file'] = str(args.title_file)

print("Installing {} model under {}... ".format(args.model_type, args.model_name), end='', flush=True)

#Get a session cookie
login_data = {}
login_data['j_username'] = "admin"
login_data['j_password'] = ADMIN_PASSWORD
login_data['j_validate'] = 'true'
cookie_resp = requests.post(CARDS_URL + "/j_security_check", data=login_data)
cards_session_cookie = cookie_resp.cookies['sling.formauth']

#Use the session cookie to make a model creation request to NeuralCR
model_install_resp = requests.put(CARDS_URL + "/ncr/models/" + args.model_name, json=install_request, cookies={'sling.formauth': cards_session_cookie})
if model_install_resp.status_code != 200:
  print("Failed")
  sys.exit(-1)

print("Done")
