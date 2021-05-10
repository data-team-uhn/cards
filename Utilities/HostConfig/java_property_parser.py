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

# This program leverages Python's argparse module to process the values
# of properties passed on the Java command line as `-Dproperty=value`
# arguments.

import os
import sys
import json
import argparse

if 'EXPR' not in os.environ:
  sys.exit(-1)

try:
  EXPR = json.loads(os.environ['EXPR'])
except:
  sys.exit(-1)

argparser = argparse.ArgumentParser(add_help=False)
argparser.add_argument('-D', action='append', nargs='*')
args, unknown_args = argparser.parse_known_args()

if EXPR['operation'] == 'includes':
  if args.D is not None:
    for javaProp in args.D:
      propKey = javaProp[0].split('=')[0]
      propVals = javaProp[0].split('=')[1].split(',')
      if (propKey == EXPR['key']) and (EXPR['val'] in propVals):
        sys.exit(0)

sys.exit(-1)
