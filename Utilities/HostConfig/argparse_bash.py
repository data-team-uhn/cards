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

# This program leverages Python's argparse module to extract an argument
# from the set of all arguments passed to a shell script

import os
import sys
import argparse

if 'ARGUMENT_KEY' not in os.environ:
  sys.exit(-1)

if 'ARGUMENT_DEFAULT' not in os.environ:
  sys.exit(-1)

ARGUMENT_KEY = os.environ['ARGUMENT_KEY']
ARGUMENT_DEFAULT = os.environ['ARGUMENT_DEFAULT']

argparser = argparse.ArgumentParser(add_help=False)
argparser.add_argument(ARGUMENT_KEY)
args, unknown_args = argparser.parse_known_args()

res = vars(args)[ARGUMENT_KEY.lstrip('-')]
if res is None:
  print(ARGUMENT_DEFAULT)
else:
  print(res)
