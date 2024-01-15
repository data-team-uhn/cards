#!/usr/bin/env python
# -*- coding: utf-8 -*-

# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

import os
import sys
import json

SLING_REQUIRED_FEATURES_FILE = sys.argv[1]

with open(SLING_REQUIRED_FEATURES_FILE, 'r') as f:
  SLING_REQUIRED_FEATURES = json.load(f)

if 'CARDS_PROJECT' not in os.environ:
  print("")
  sys.exit(1)

CARDS_PROJECT = os.environ['CARDS_PROJECT']

if CARDS_PROJECT not in SLING_REQUIRED_FEATURES:
  print("")
  sys.exit(1)

features_template_list = SLING_REQUIRED_FEATURES[CARDS_PROJECT]
features_list = [template.format(**os.environ) for template in features_template_list]

print(",".join(features_list))
