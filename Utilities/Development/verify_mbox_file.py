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
import mailbox

if len(sys.argv) < 3:
  print("Usage: python3 verify_mbox_file.py /path/to/mbox/file text|html")
  sys.exit(1)

MBOX_FILE_PATH = sys.argv[1]
MESSAGE_TYPE = sys.argv[2]

if (MESSAGE_TYPE not in ['text', 'html']):
  print("Invalid message type specified - use either 'text' or 'html'")
  sys.exit(1)

EXPECTED_FROM = 'UHN DATAPRO <datapro@uhn.ca>'
EXPECTED_TO = 'Test User <testuser@mail.com>'
EXPECTED_SUBJECT = 'CARDS-UHN Test Message'
EXPECTED_HTML_BODY = "<html><head><title>Rich Text</title></head><body><p>Here is a test message from CARDS at the University Health Network</p></body></html>"
EXPECTED_PLAIN_BODY = "Here is a test message from CARDS at the University Health Network"

mbox = mailbox.mbox(MBOX_FILE_PATH)
assert 1 == len(mbox), "Invalid number of messages in MBOX file: {}".format(len(mbox))

message = mbox[0]
assert EXPECTED_FROM == message['From'], "Invalid FROM"
assert EXPECTED_TO == message['To'], "Invalid TO"
assert EXPECTED_SUBJECT == message['Subject'], "Invalid SUBJECT"

if MESSAGE_TYPE == 'html':
  assert 'multipart/mixed' == message.get_content_type(), "Not a multipart/mixed message"
  payload = message.get_payload()
  assert 1 == len(payload), "Invalid payload length"
  payload = payload[0]
  assert 'multipart/alternative' == payload.get_content_type(), "Not a multipart/alternative message"
  payload = payload.get_payload()
  assert 2 == len(payload), "multipart/alternative message does not contain two messages"
  assert 'text/html' == payload[0].get_content_type(), "First message is not text/html"
  assert 'text/plain' == payload[1].get_content_type(), "Second message is not text/plain"
  assert EXPECTED_HTML_BODY == payload[0].get_payload(), "HTML message body does not match expected value"
  assert EXPECTED_PLAIN_BODY == payload[1].get_payload(), "Plain message body does not match expected value"

elif MESSAGE_TYPE == 'text':
  assert 'text/plain' == message.get_content_type(), "Not a text/plain message"
  assert (EXPECTED_PLAIN_BODY + '\n') == message.get_payload(), "Plain message body does not match expected value"

print("OK - All tests passed!")
