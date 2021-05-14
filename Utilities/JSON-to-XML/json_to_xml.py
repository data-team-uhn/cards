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
from collections import OrderedDict

JSON_QUESTIONNAIRE = sys.argv[1]
with open(JSON_QUESTIONNAIRE, 'r') as f_json:
    questionnaire = json.loads(f_json.read(), object_pairs_hook=OrderedDict)

def get_jcr_type(d):
    d_copy = d
    if (type(d) == list):
        if (len(d) == 0):
            return "String"
        else:
            d_copy = d[0]

    if (type(d_copy) == int):
        return "Long"
    elif (type(d_copy) == float):
        return "Double"
    elif (type(d_copy) == bool):
        return "Boolean"
    else:
        return "String"

def convert_xml_safe(itm):
    if str(itm) in ["<>", "<", ">"] or (isinstance(itm, str) and ("<" in itm or ">" in itm or "&" in itm)):
        return "<![CDATA[" + str(itm) + "]]>"
    return str(itm)

def save_list(l, indentation_level):
    print("\t"*indentation_level + "<values>")
    for itm in l:
        print("\t"*(indentation_level+1) + "<value>" + convert_xml_safe(itm) + "</value>")
    print("\t"*indentation_level + "</values>")

def save_as_xml(d, node_name="XmlQuestionnaire", indentation_level=0):
    print("\t"*indentation_level + "<node>")
    #Add the name
    print("\t"*(indentation_level+1) + "<name>" + convert_xml_safe(node_name) + "</name>")

    #First, just print the static values
    for key in d.keys():
        val = d[key]
        if (type(val) != OrderedDict):
            if (key == "jcr:primaryType"):
                print("\t"*(indentation_level+1) + "<primaryNodeType>" + convert_xml_safe(val) + "</primaryNodeType>")
            else:
                print("\t"*(indentation_level+1) + "<property>")
                if (key.startswith("jcr:reference:")):
                    print("\t"*(indentation_level+2) + "<name>" + key[len("jcr:reference:"):] + "</name>")
                    if (type(val) == list):
                        save_list(val, indentation_level+2)
                    else:
                        print("\t"*(indentation_level+2) + "<value>" + convert_xml_safe(val) + "</value>")
                    print("\t"*(indentation_level+2) + "<type>Reference</type>")
                else:
                    print("\t"*(indentation_level+2) + "<name>" + key + "</name>")
                    if (type(val) == list):
                        save_list(val, indentation_level+2)
                    else:
                        print("\t"*(indentation_level+2) + "<value>" + convert_xml_safe(val) +  "</value>")
                    print("\t"*(indentation_level+2) + "<type>" + get_jcr_type(val) + "</type>")
                print("\t"*(indentation_level+1) + "</property>")

    #Then, print the internal nodes
    for key in d.keys():
        val = d[key]
        if (type(val) == OrderedDict):
            save_as_xml(val, key, indentation_level+1)
    print("\t"*indentation_level + "</node>")

xml_name = JSON_QUESTIONNAIRE
if (xml_name.endswith('.json')):
    xml_name = xml_name[:-1*len('.json')]

print("""<!--
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
-->
""")
save_as_xml(questionnaire, node_name=xml_name)
