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

import sys
import json
import os
from collections import OrderedDict

RAW_JSON = sys.argv[1]
with open(RAW_JSON, 'r') as f_json:
    questionnaire = json.loads(f_json.read(), object_pairs_hook=OrderedDict)

def process_node(node):
    ordered_properties = [
        "jcr:primaryType",
        "title",
        "text",
        "description",
        "maxAnswers",
        "minAnswers",
        "dataType",
        "entryMode",
        "displayMode"
    ]
    ordered_properties.reverse()
    for prop in ordered_properties:
        if prop in node.keys():
            node.move_to_end(prop, last=False)
    for key in node.keys():
        val = node[key]
        if type(val) == OrderedDict:
            process_node(val)

process_node(questionnaire)
with open(RAW_JSON, 'w') as output:
    json.dump(questionnaire, output, indent='\t')
    output.close()
    os.system("python3 ../JSON-to-XML/json_to_xml.py '" + RAW_JSON + "' > '" + RAW_JSON[:-5] + ".xml'")
