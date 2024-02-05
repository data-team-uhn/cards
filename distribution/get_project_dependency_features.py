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

"""
Get a list of the keys in a Python format string

For example: gatherFormatStringKeys("Hello {planet} my name is {name}.")
would return ["planet", "name"]
"""
def gatherFormatStringKeys(fstring):
  placeholders = {}
  while True:
    try:
      fstring.format(**placeholders)
      return list(placeholders.keys())
    except KeyError as e:
      placeholders[e.args[0]] = ""

"""
Determines the values that are to be used for each key.

Going left-to-right, the key is assigned the first available value

For example: `$name|John` would first try to use the value of the `name`
variable. If the `name` variable is not defined, it would use the
literal value "John".
"""
def buildFormatStringValuesMap(keys, variables):
  kvmap = {}
  for key in keys:
    for v in key.split('|'):
      if v.startswith('$'):
        # This is a reference to a variable
        varname = v[1:]
        if varname in variables:
          kvmap[key] = variables[varname]
          break
      else:
        # Otherwise this is a immediate value
        kvmap[key] = v
        break
  return kvmap

"""
Renders a format string using the available values

For example:

>>> renderFormatString("Hello {$name|there}!", {})
Hello there!

>>> renderFormatString("Hello {$name|there}!, {'name': 'John'})
Hello John!
"""
def renderFormatString(fstring, variables):
  keys = gatherFormatStringKeys(fstring)
  varmap = buildFormatStringValuesMap(keys, variables)
  return fstring.format(**varmap)

"""
Return a copy of a passed dictionary and remove any key-value pairs
which have a value of an empty string

>>> removeEmptyKeys({"foo": "bar", "hello": ""})
{"foo": "bar"}
"""
def removeEmptyKeys(d):
  return {k: d[k] for k in d if d[k] != ""}

SLING_REQUIRED_FEATURES_FILE = sys.argv[1]

with open(SLING_REQUIRED_FEATURES_FILE, 'r') as f:
  SLING_REQUIRED_FEATURES = json.load(f)

if 'PROJECT_NAME' not in os.environ:
  PROJECT_NAME = ""
else:
  PROJECT_NAME = os.environ['PROJECT_NAME']

if PROJECT_NAME not in SLING_REQUIRED_FEATURES:
  print("")
  sys.exit(1)

features_template_list = SLING_REQUIRED_FEATURES[PROJECT_NAME]
features_list = [renderFormatString(template, removeEmptyKeys(os.environ)) for template in features_template_list]

print(",".join(features_list))
