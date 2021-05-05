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

# This program checks if a pid is listening on a tcp port
# Exit status 0 if yes. Exit status 1 if no.

import sys
import psutil
import argparse

argparser = argparse.ArgumentParser()
argparser.add_argument('--tcp_port', help='The TCP port to check', type=int, required=True)
argparser.add_argument('--pid', help='The process identifier (PID) to check', type=int, required=True)
args = argparser.parse_args()

for conn in psutil.net_connections(kind='tcp'):
  if conn.status == 'LISTEN':
    if (conn.pid == args.pid) and (conn.laddr.port == args.tcp_port):
      sys.exit(0)

sys.exit(1)
