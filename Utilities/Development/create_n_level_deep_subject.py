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

from create_subject_type import create_subject_type
from create_subject_for_type import create_subject_for_type

import argparse

def create_n_level_deep_subject(n):
  # Create the Subject Types
  new_subject_type_parent = "/SubjectTypes"
  new_subject_parent = "/Subjects"
  for subject_type_level in range(n):
    new_subject_type_name = "subjectlevel" + str(subject_type_level)
    new_subject_type_path = new_subject_type_parent + "/" + new_subject_type_name
    create_subject_type(new_subject_type_parent, new_subject_type_name)
    print("Created {} ...".format(new_subject_type_path))
    new_subject_identifier = "S" + str(subject_type_level)
    new_subject_parent = create_subject_for_type(new_subject_parent, new_subject_identifier, new_subject_type_path)
    print("Created {} ...".format(new_subject_parent))
    new_subject_type_parent += "/" + new_subject_type_name

if __name__ == '__main__':
  argparser = argparse.ArgumentParser()
  argparser.add_argument('--n', help='How many levels deep this subject should be created at', type=int, required=True)
  args = argparser.parse_args()
  create_n_level_deep_subject(args.n)
