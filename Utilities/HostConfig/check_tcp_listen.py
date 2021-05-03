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

import re
import os
import sys
import argparse

argparser = argparse.ArgumentParser()
argparser.add_argument('--tcp_port', help='The TCP port to check', type=int, required=True)
argparser.add_argument('--pid', help='The process identifier (PID) to check', type=int, required=True)
args = argparser.parse_args()

def checkProcNet(tcp_version, inode, port):
  LOCAL_ADDR_POS = 1
  CONNECTION_STATE_POS = 3
  INODE_POS = 9
  CONNECTION_STATE_VAL_LISTEN = 0x0A
  initialized = False
  with open('/proc/net/' + tcp_version, 'r') as net_connections:
    for connection_line in net_connections.readlines():
      connection_line_items = connection_line.split()
      if not initialized:
        initialized = True
        continue
      line_local_addr = connection_line_items[LOCAL_ADDR_POS]
      line_inode = connection_line_items[INODE_POS]
      if line_inode == inode:
        line_port = int(line_local_addr.split(':')[1], 16)
        if line_port == port:
          line_state = int(connection_line_items[CONNECTION_STATE_POS], 16)
          if CONNECTION_STATE_VAL_LISTEN == line_state:
            return True
  return False

# Obtain the set of sockets associated with --pid
proc_fd_path = "/proc/{}/fd/".format(args.pid)
for fd in os.listdir(proc_fd_path):
  fd_resource = os.readlink(os.path.join(proc_fd_path, fd))
  if fd_resource.startswith("socket:"):
    socket_inode = re.search('socket:\[(\d+)\]', fd_resource).group(1)
    # Now check /proc/net/tcp and /proc/net/tcp6
    if checkProcNet('tcp', socket_inode, args.tcp_port):
      sys.exit(0)
    if checkProcNet('tcp6', socket_inode, args.tcp_port):
      sys.exit(0)

sys.exit(1)
