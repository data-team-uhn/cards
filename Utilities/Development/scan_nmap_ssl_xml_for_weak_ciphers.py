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

"""
Checks the results of an SSL cipher scan done by:

nmap --script ssl-enum-ciphers -p 443 localhost -oX ssl_scan.xml

"""

import sys
import defusedxml.ElementTree as ET

WEAK_CIPHERS = []
WEAK_CIPHERS.append("TLS_RSA_WITH_AES_128_CBC_SHA")
WEAK_CIPHERS.append("TLS_DHE_RSA_WITH_AES_128_CBC_SHA")
WEAK_CIPHERS.append("TLS_RSA_WITH_AES_256_CBC_SHA")
WEAK_CIPHERS.append("TLS_DHE_RSA_WITH_AES_256_CBC_SHA")
WEAK_CIPHERS.append("TLS_RSA_WITH_AES_128_CBC_SHA256")
WEAK_CIPHERS.append("TLS_RSA_WITH_AES_256_CBC_SHA256")
WEAK_CIPHERS.append("TLS_DHE_RSA_WITH_AES_128_CBC_SHA256")
WEAK_CIPHERS.append("TLS_DHE_RSA_WITH_AES_256_CBC_SHA256")
WEAK_CIPHERS.append("TLS_RSA_WITH_CAMELLIA_128_CBC_SHA")
WEAK_CIPHERS.append("TLS_DHE_RSA_WITH_CAMELLIA_128_CBC_SHA")
WEAK_CIPHERS.append("TLS_RSA_WITH_CAMELLIA_256_CBC_SHA")
WEAK_CIPHERS.append("TLS_DHE_RSA_WITH_CAMELLIA_256_CBC_SHA")
WEAK_CIPHERS.append("TLS_RSA_WITH_AES_128_GCM_SHA256")
WEAK_CIPHERS.append("TLS_RSA_WITH_AES_256_GCM_SHA384")
WEAK_CIPHERS.append("TLS_RSA_WITH_CAMELLIA_128_CBC_SHA256")
WEAK_CIPHERS.append("TLS_DHE_RSA_WITH_CAMELLIA_128_CBC_SHA256")
WEAK_CIPHERS.append("TLS_RSA_WITH_CAMELLIA_256_CBC_SHA256")
WEAK_CIPHERS.append("TLS_DHE_RSA_WITH_CAMELLIA_256_CBC_SHA256")
WEAK_CIPHERS.append("TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA")
WEAK_CIPHERS.append("TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA")
WEAK_CIPHERS.append("TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256")
WEAK_CIPHERS.append("TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384")
WEAK_CIPHERS.append("TLS_ECDHE_RSA_WITH_CAMELLIA_128_CBC_SHA256")
WEAK_CIPHERS.append("TLS_ECDHE_RSA_WITH_CAMELLIA_256_CBC_SHA384")

XML_FILE = sys.argv[1]

with open(XML_FILE, 'r') as f_xml:
  xml_tree = ET.fromstring(f_xml.read())

for elem in xml_tree.findall(".//elem"):
  if 'key' in elem.attrib:
    if elem.attrib['key'] == 'name':
      cipher_name = elem.text
      print("Checking cipher: {}".format(cipher_name))
      if cipher_name in WEAK_CIPHERS:
        raise Exception("FAIL: The server allows the weak cipher: {}".format(cipher_name))

print("PASS: The server does not allow any weak ciphers.")
