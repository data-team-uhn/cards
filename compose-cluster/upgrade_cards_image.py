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

import yaml
import argparse

argparser = argparse.ArgumentParser()
argparser.add_argument('--cards_docker_tag', help='Switch to this tagged version of the CARDS Docker image', required=True)
args = argparser.parse_args()

# YAML load
with open('docker-compose.yml', 'r') as f_yaml:
  yaml_obj = yaml.load(f_yaml.read(), Loader=yaml.SafeLoader)

# Do the replacement
new_cards_image = yaml_obj['services']['cardsinitial']['image']
new_cards_image = new_cards_image.split(":")[0] + ":" + args.cards_docker_tag
yaml_obj['services']['cardsinitial']['image'] = new_cards_image

# YAML save
with open('docker-compose.yml', 'w') as f_yaml:
  f_yaml.write(yaml.dump(yaml_obj, default_flow_style=False))

print("Upgraded CARDS Docker image to: {}".format(args.cards_docker_tag))
