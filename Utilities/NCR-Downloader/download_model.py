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
import tarfile
import hashlib
import argparse
import requests

DEFAULT_MANIFEST_FILE = 'model_repo.json'
TAR_GZ_TYPES = ['NeuralCR', 'BasicCR']

argparser = argparse.ArgumentParser()
argparser.add_argument('--manifest', help='Specify an alternate models manifest file', default=DEFAULT_MANIFEST_FILE)
argparser.add_argument('--list', help='List the models available for download', action='store_true')
argparser.add_argument('--download', help='Download a model')
argparser.add_argument('--savedir', help='Directory to save downloaded model')
args = argparser.parse_args()

MANIFEST_FILE = DEFAULT_MANIFEST_FILE
if args.manifest:
  MANIFEST_FILE = args.manifest

with open(MANIFEST_FILE, 'r') as f_json:
  MODEL_REPO = json.loads(f_json.read())

if args.list:
  for model_name in MODEL_REPO:
    model = MODEL_REPO[model_name]
    model_title = model_name + " [" + model['type'] + "]"
    print(model_title)
    print('-'*len(model_title))
    print(model['description'])
    print('')
  sys.exit()

if args.download:
  if args.savedir is None:
    print("Must specify a directory to save the downloaded model")
    sys.exit(-1)
  if args.download not in MODEL_REPO:
    print("Unknown model")
    sys.exit(-1)
  model_url = MODEL_REPO[args.download]['url']
  model_sha256 = MODEL_REPO[args.download]['sha256']
  model_type = MODEL_REPO[args.download]['type']
  savedir = args.savedir.rstrip('/')
  download_filename = "{}/{}.part".format(savedir, model_sha256)
  print("Downloading {} from {}".format(args.download, model_url))
  h_sha256 = hashlib.sha256()

  #Download
  with requests.get(model_url, stream=True) as download_stream:
    download_stream.raise_for_status()
    with open(download_filename, 'wb') as save_stream:
      for chunk in download_stream.iter_content(chunk_size=8192):
        save_stream.write(chunk)
        h_sha256.update(chunk)

  #Verify the downloaded file
  if model_sha256 != h_sha256.hexdigest():
    #Cleanup
    os.remove(download_filename)
    print("Downloaded model integrity check failed")
    sys.exit(-1)

  #Extract the .tar.gz file if necessary
  if model_type in TAR_GZ_TYPES:
    print("Uncompressing...")
    model_archive = tarfile.open(download_filename, 'r:gz')
    model_archive.extractall(path=savedir)
    model_archive.close()
    #Remove the downloaded .tar.gz file
    os.remove(download_filename)
  else:
    #Rename the downloaded file to its proper name
    os.rename(download_filename, "{}/{}".format(savedir, args.download))

print("Done")
