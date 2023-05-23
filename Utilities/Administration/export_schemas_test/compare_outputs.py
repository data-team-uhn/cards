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
import json

def loadJSONFile(filepath):
	with open(filepath, 'r') as f_json:
		return json.load(f_json)

for filename in os.listdir('ORIGINAL_SCRIPT_OUTPUT'):
	if filename == '.gitignore':
		continue
	original_output_path = os.path.join('ORIGINAL_SCRIPT_OUTPUT', filename)
	new_output_path = os.path.join('NEW_SCRIPT_OUTPUT', filename)
	print("Comparing {} to {}".format(original_output_path, new_output_path))
	assert loadJSONFile(original_output_path) == loadJSONFile(new_output_path)
	print("OK")

print("SUCCESS")
