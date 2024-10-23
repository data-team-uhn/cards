#
#  Licensed to the Apache Software Foundation (ASF) under one
#  or more contributor license agreements.  See the NOTICE file
#  distributed with this work for additional information
#  regarding copyright ownership.  The ASF licenses this file
#  to you under the Apache License, Version 2.0 (the
#  "License"); you may not use this file except in compliance
#  with the License.  You may obtain a copy of the License at
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing,
#  software distributed under the License is distributed on an
#  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
#  KIND, either express or implied.  See the License for the
#  specific language governing permissions and limitations
# under the License.
#

from os import listdir
from os.path import isfile
import argparse

import xml.etree.ElementTree as ET

# Check all the .xml files in either the current folder or in a specified folder for
# any Questions or Sections with duplicate names.
# Output:
# - A list of all searched files
# - Duplicate names as they are found
# - A summary for each file

# Sample usage:
#
# ~/cards/Utilities/NameDuplicates$ python nameDuplicates.py
# ['test.xml']
# Question2
# Section1
# Parsed test.xml. Invalid: 0. Duplicates: 2. Parsed Nodes: 15
#
# ~/cards/Utilities/FormImport/test$ python nameDuplicates.py --path /home/acrow/cards/proms-resources/clinical-data/src/main/resources/SLING-INF/content/Questionnaires
# ['AUDITC.xml', 'EQ5D.xml', 'SC.xml', 'PHQ9.xml', 'GAD7.xml', 'SF12.xml']
# Parsed AUDITC.xml. Invalid: 0. Duplicates: 0. Parsed Nodes: 25
# Parsed EQ5D.xml. Invalid: 0. Duplicates: 0. Parsed Nodes: 26
# smoking-history-v2-referral-summary
# Parsed SC.xml. Invalid: 0. Duplicates: 1. Parsed Nodes: 85
# Parsed PHQ9.xml. Invalid: 0. Duplicates: 0. Parsed Nodes: 70
# Parsed GAD7.xml. Invalid: 0. Duplicates: 0. Parsed Nodes: 59
# Parsed SF12.xml. Invalid: 0. Duplicates: 0. Parsed Nodes: 46

names = set()
invalidNodes = 0
duplicateNames = 0
parsedNodes = 0

def parseNode(node):
    global duplicateNames
    global invalidNodes
    global names
    global parsedNodes

    primaryNodeType = None
    name = None
    parsedNodes += 1
    for child in node:
        if child.tag == "primaryNodeType":
            primaryNodeType = child.text
        if child.tag == "node":
            parseNode(child)
        if child.tag == "name":
            name = child.text
    if "cards:Section" == primaryNodeType or "cards:Question" == primaryNodeType:
        if name == None:
            invalidNodes += 1
        else:
            if name in names:
                print(name)
                duplicateNames += 1
            else:
                names.add(name)


CLI = argparse.ArgumentParser()
CLI.add_argument("--path", type=str, required=False)
args = CLI.parse_args()
path = args.path + "/" if args.path != None else "./"

xmls = [f for f in listdir(path) if isfile(path + f) and f.endswith(".xml")]

print(xmls)

for xml in xmls:

    names = set()
    invalidNodes = 0
    duplicateNames = 0
    parsedNodes = 0

    tree = ET.parse(path + xml)
    root = tree.getroot()

    parseNode(root)
    print("Parsed " + xml + ". Invalid: " + str(invalidNodes) + ". Duplicates: " + str(duplicateNames) + ". Parsed Nodes: " + str(parsedNodes))
