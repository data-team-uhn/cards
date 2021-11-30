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

for d in os.walk('.'):
  if d[0].startswith('./.git/'):
    continue
  if d[0].startswith('./sling/'):
    continue
  if d[0].startswith('./.cards-data/'):
    continue
  if '/target/' in d[0]:
    continue
  if '/node_modules/' in d[0]:
    continue
  if len(d[2]) == 0:
    continue
  if d[0].endswith('/src/main/resources/SLING-INF/content/apps/cards/ExtensionPoints'):
    osgi_module_path = d[0]
    for node_file in d[2]:
      print("--> {}".format(os.path.join(d[0], node_file)))
