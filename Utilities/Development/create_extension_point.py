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

# Collect the ID, Name, and Description for the ExtensionPoint
EXTENSION_POINT_NAME = input("Enter the name for this ExtensionPoint to be used in JCR: ")
EXTENSION_POINT_ID = input("Enter the ID for this ExtensionPoint (eg. cards/coreUI/myExtensionPoint): ")
EXTENSION_POINT_DESC = input("Enter the description for this ExtensionPoint: ")

# Where in the repository should this ExtensionPoint be placed ?
OSGI_MODULE_DIRECTORY = input("Enter the path to the OSGi module that will provide this ExtensionPoint: ")

# List all the JSX files beloning to this OSGi module
jsx_files = []
for walk_entry in os.walk(os.path.join(OSGI_MODULE_DIRECTORY, 'src')):
  basepath = walk_entry[0]
  for filename in walk_entry[2]:
    if filename.endswith('.jsx'):
      jsx_files.append(os.path.join(basepath, filename))

print("Available JSX files")
print("-------------------")
for jsx_file in jsx_files:
  print("\t--> {}".format(jsx_file))

JSX_FILE_UNDER_EDIT = input("Enter the JSX file to add this ExtensionPoint to: ")

# Create the JSON file under ${OSGI_MODULE_DIRECTORY}/src/main/resources/SLING-INF/content/apps/cards/ExtensionPoints/${name}.json
try:
  os.makedirs(os.path.join(OSGI_MODULE_DIRECTORY, "src/main/resources/SLING-INF/content/apps/cards/ExtensionPoints"))
except FileExistsError:
  pass

EXTENSION_POINT_CONFIG = {}
EXTENSION_POINT_CONFIG['jcr:primaryType'] = 'cards:ExtensionPoint'
EXTENSION_POINT_CONFIG['cards:extensionPointId'] = EXTENSION_POINT_ID
EXTENSION_POINT_CONFIG['cards:extensionPointName'] = EXTENSION_POINT_DESC
with open(os.path.join(OSGI_MODULE_DIRECTORY, "src/main/resources/SLING-INF/content/apps/cards/ExtensionPoints/{}.json".format(EXTENSION_POINT_NAME)), 'w') as f_json:
  f_json.write(json.dumps(EXTENSION_POINT_CONFIG, indent=2))

# Check that pom.xml will load this ExtensionPoint and, if needed, describe how it should be modified
pomWillLoad = False
with open(os.path.join(OSGI_MODULE_DIRECTORY, "pom.xml"), 'r') as f_pom:
  for pomline in f_pom.readlines():
    if "path:=/apps/cards/ExtensionPoints/" in pomline:
      pomWillLoad = True

if not pomWillLoad:
  print("WARNING: The {}/pom.xml doesn't look like it will load this ExtensionPoint into the JCR.")
  print("Please ensure that this is configured appropriately in the <Sling-Initial-Content> section")

# Create a use case template for this ExtensionPoint
with open("Utilities/Development/ExtensionPointResources/ExtensionPointUserTemplate.jsx", 'r') as f:
  extensionPointUseJsx = f.read()

extensionPointUseJsx = extensionPointUseJsx.replace("_____DEFAULT_FUNCTION_NAME_____", EXTENSION_POINT_NAME)
extensionPointUseJsx = extensionPointUseJsx.replace("_____EXTENSION_POINT_NAME_____", EXTENSION_POINT_NAME)

with open(os.path.join(os.path.dirname(JSX_FILE_UNDER_EDIT), "{}.jsx".format(EXTENSION_POINT_NAME)), 'w') as f:
  f.write(extensionPointUseJsx)

# Describe how to modify the JSX_FILE_UNDER_EDIT
print("To use this UI ExtensionPoint:")
print("  - Edit the file: {}".format(JSX_FILE_UNDER_EDIT))
print("  - Add the line 'import {} from \"./{}.jsx\";' to imports section.".format(EXTENSION_POINT_NAME, EXTENSION_POINT_NAME))
print("  - Add the line `<{} />` to use the component".format(EXTENSION_POINT_NAME))
