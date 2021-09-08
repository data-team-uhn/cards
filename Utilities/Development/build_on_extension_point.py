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

PROJECT_NAME_PREFIX = "cards"

# Specify the path to the JSON file defining the extensionPoint that we are attaching to
ATTACHING_EXTENSION_POINT_PATH = input("Enter the path to the JSON file for the extensionPoint to attach to: ")
with open(ATTACHING_EXTENSION_POINT_PATH, 'r') as f_attaching_json:
  ATTACHING_EXTENSION_POINT = json.loads(f_attaching_json.read())
ATTACHING_EXTENSION_POINT_NAME = ATTACHING_EXTENSION_POINT_PATH.split('/')[-1].split('.')[0]

# Gather information describing this UI extension OSGi module
THIS_OSGI_MODULE_DIRECTORY = input("Enter the path to the OSGi module directory for this extension: ")
THIS_OSGI_MODULE_NAME = THIS_OSGI_MODULE_DIRECTORY.split('/')[-1]

# Configuration for the extension's JCR Node under the /Extensions/ path
THIS_EXTENSION_JCRNODE_NAME = input("Enter the name for this JCR extension node: ")
THIS_EXTENSION_HUMAN_NAME = input("Enter the human-readable name for this extension: ")
ATTACHING_EXTENSION_POINT_ID = ATTACHING_EXTENSION_POINT['cards:extensionPointId']

# Configuration for the extension's package.json file
THIS_EXTENSION_VERSION = input("Enter the version of this extension: ")
THIS_EXTENSION_DESCRIPTION = input("Enter the description of this extension: ")
THIS_EXTENSION_AUTHOR = input("Enter the author of this extension: ")
THIS_EXTENSION_LICENSE = input("Enter the license of this extension: ")
THIS_EXTENSION_REPO_URL = input("Enter the repository URL for this extension: ")
THIS_EXTENSION_REPO_DIRECTORY = input("Enter the repository directory for this extension: ")
THIS_EXTENSION_JSX_SRC = input("Enter the name of the JSX source file (without the .jsx): ")

# Create the src/main/resources/SLING-INF/content/ directory
try:
  os.makedirs(os.path.join(THIS_OSGI_MODULE_DIRECTORY, "src/main/resources/SLING-INF/content/"))
except FileExistsError:
  pass

# Create the src/main/frontend/ directory
try:
  os.makedirs(os.path.join(THIS_OSGI_MODULE_DIRECTORY, "src/main/frontend/"))
except FileExistsError:
  pass

# Add a package.json file
with open("Utilities/Development/ExtensionPointResources/package.json", 'r') as f_package_json:
  package_json = json.loads(f_package_json.read())
package_json['name'] = PROJECT_NAME_PREFIX + "-" + THIS_OSGI_MODULE_NAME
package_json['version'] = THIS_EXTENSION_VERSION
package_json['description'] = THIS_EXTENSION_DESCRIPTION
package_json['author'] = THIS_EXTENSION_AUTHOR
package_json['license'] = THIS_EXTENSION_LICENSE
package_json['repository']['url'] = THIS_EXTENSION_REPO_URL
package_json['repository']['directory'] = THIS_EXTENSION_REPO_DIRECTORY
with open(os.path.join(THIS_OSGI_MODULE_DIRECTORY, "src/main/frontend/package.json"), 'w') as f_package_json:
  f_package_json.write(json.dumps(package_json, indent=4))

# Add a webpack.config.js
with open(os.path.join(THIS_OSGI_MODULE_DIRECTORY, "src/main/frontend/webpack.config.js"), 'w') as f_webpack_config_js:
  with open("Utilities/Development/ExtensionPointResources/webpack.config.js.head", 'r') as f_head:
    f_webpack_config_js.write(f_head.read().rstrip())

  f_webpack_config_js.write("\n")
  f_webpack_config_js.write("    [module_name + '{}']: './src/{}.jsx'".format(THIS_EXTENSION_JSX_SRC, THIS_EXTENSION_JSX_SRC))
  f_webpack_config_js.write("\n")

  with open("Utilities/Development/ExtensionPointResources/webpack.config.js.tail", 'r') as f_tail:
    f_webpack_config_js.write(f_tail.read())

extension_properties = {}
extension_properties[ATTACHING_EXTENSION_POINT_NAME] = {}
extension_properties[ATTACHING_EXTENSION_POINT_NAME]['jcr:primaryType'] = 'sling:Folder'
extension_properties[ATTACHING_EXTENSION_POINT_NAME][THIS_EXTENSION_JCRNODE_NAME] = {}
extension_properties[ATTACHING_EXTENSION_POINT_NAME][THIS_EXTENSION_JCRNODE_NAME]['jcr:primaryType'] = 'cards:Extension'
extension_properties[ATTACHING_EXTENSION_POINT_NAME][THIS_EXTENSION_JCRNODE_NAME]['cards:extensionPointId'] = ATTACHING_EXTENSION_POINT_ID
extension_properties[ATTACHING_EXTENSION_POINT_NAME][THIS_EXTENSION_JCRNODE_NAME]['cards:extensionName'] = THIS_EXTENSION_HUMAN_NAME
extension_properties[ATTACHING_EXTENSION_POINT_NAME][THIS_EXTENSION_JCRNODE_NAME]['cards:extensionRenderURL'] = "asset:{}-{}.{}.js".format(PROJECT_NAME_PREFIX, THIS_OSGI_MODULE_NAME, THIS_EXTENSION_JSX_SRC)

# Save extension_properties to a JSON file
with open(os.path.join(THIS_OSGI_MODULE_DIRECTORY, "src/main/resources/SLING-INF/content/Extensions.json"), 'w') as f_extension_properties:
  f_extension_properties.write(json.dumps(extension_properties, indent=4))

# Add the src/*.jsx file
# ...create the src/main/frontend/src directory
try:
  os.makedirs(os.path.join(THIS_OSGI_MODULE_DIRECTORY, "src/main/frontend/src"))
except FileExistsError:
  pass

# Create a boilerplate JSX component
with open(os.path.join(THIS_OSGI_MODULE_DIRECTORY, "src/main/frontend/src/{}.jsx".format(THIS_EXTENSION_JSX_SRC)), 'w') as f_jsx:
  with open("Utilities/Development/ExtensionPointResources/extension_boilerplate.jsx", 'r') as f_boilerplate:
    f_jsx.write(f_boilerplate.read())

  f_jsx.write("\n\n\n")
  f_jsx.write("export default function {}(props)".format(THIS_EXTENSION_JSX_SRC) + " {\n")
  f_jsx.write("  return null;\n")
  f_jsx.write("}\n")

# Create a pom.xml file for this OSGi UI ExtensionPoint module
with open(os.path.join(THIS_OSGI_MODULE_DIRECTORY, "pom.xml"), 'w') as f_pomxml:
  with open("Utilities/Development/ExtensionPointResources/pom.xml.head", 'r') as f_head:
    f_pomxml.write(f_head.read().rstrip())

  f_pomxml.write("\n")
  f_pomxml.write("  <artifactId>cards-modules-{}</artifactId>\n".format(THIS_OSGI_MODULE_NAME))
  f_pomxml.write("  <packaging>bundle</packaging>\n")
  f_pomxml.write("  <name>{} - {}</name>\n".format(PROJECT_NAME_PREFIX.upper(), THIS_EXTENSION_HUMAN_NAME))

  with open("Utilities/Development/ExtensionPointResources/pom.xml.tail", 'r') as f_tail:
    f_pomxml.write(f_tail.read())

# Show the developer how to build and use this OSGi module
print("OSGi module directory created. To use it:")
print("  - Add <module>{}</module> to the modules/pom.xml file".format(THIS_OSGI_MODULE_NAME))
print("  - Add io.uhndata.cards/cards-modules-{}/".format(THIS_OSGI_MODULE_NAME) + "${cards.version} to a distribution/src/main/provisioning file")
