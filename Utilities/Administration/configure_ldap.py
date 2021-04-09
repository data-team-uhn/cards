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
import sys
import requests
from requests.auth import HTTPBasicAuth

CARDS_URL = "http://localhost:8080"
if "CARDS_URL" in os.environ:
  CARDS_URL = os.environ["CARDS_URL"].rstrip('/')

ADMIN_PASSWORD = "admin"
if "ADMIN_PASSWORD" in os.environ:
  ADMIN_PASSWORD = os.environ["ADMIN_PASSWORD"]

CARDS_LDAP_PROVIDER_NAME = "ldap"
if "CARDS_LDAP_PROVIDER_NAME" in os.environ:
  CARDS_LDAP_PROVIDER_NAME = os.environ["CARDS_LDAP_PROVIDER_NAME"]

if "CARDS_LDAP_HOSTNAME" in os.environ:
  CARDS_LDAP_HOSTNAME = os.environ["CARDS_LDAP_HOSTNAME"]
else:
  CARDS_LDAP_HOSTNAME = input("LDAP server hostname: ")

CARDS_LDAP_PORT = "389"
if "CARDS_LDAP_PORT" in os.environ:
  CARDS_LDAP_PORT = os.environ["CARDS_LDAP_PORT"]

CARDS_LDAP_SSL = "false"
if "CARDS_LDAP_SSL" in os.environ:
  CARDS_LDAP_SSL = os.environ["CARDS_LDAP_SSL"]

CARDS_LDAP_TLS = "false"
if "CARDS_LDAP_TLS" in os.environ:
  CARDS_LDAP_TLS = os.environ["CARDS_LDAP_TLS"]

CARDS_LDAP_NO_CERT_CHECK = "false"
if "CARDS_LDAP_NO_CERT_CHECK" in os.environ:
  CARDS_LDAP_NO_CERT_CHECK = os.environ["CARDS_LDAP_NO_CERT_CHECK"]

if "CARDS_LDAP_BIND_DN" in os.environ:
  CARDS_LDAP_BIND_DN = os.environ["CARDS_LDAP_BIND_DN"]
else:
  CARDS_LDAP_BIND_DN = input("LDAP Bind DN: ")

if "CARDS_LDAP_BIND_PASSWORD" in os.environ:
  CARDS_LDAP_BIND_PASSWORD = os.environ["CARDS_LDAP_BIND_PASSWORD"]
else:
  CARDS_LDAP_BIND_PASSWORD = input("LDAP Bind Password: ")

if "CARDS_LDAP_USER_BASE_DN" in os.environ:
  CARDS_LDAP_USER_BASE_DN = os.environ["CARDS_LDAP_USER_BASE_DN"]
else:
  CARDS_LDAP_USER_BASE_DN = input("LDAP User Base DN: ")

if "CARDS_LDAP_USER_OBJECT_CLASS" in os.environ:
  CARDS_LDAP_USER_OBJECT_CLASS = os.environ["CARDS_LDAP_USER_OBJECT_CLASS"]
else:
  CARDS_LDAP_USER_OBJECT_CLASS = input("LDAP User Object Class: ")

if "CARDS_LDAP_USER_ID_ATTRIBUTE" in os.environ:
  CARDS_LDAP_USER_ID_ATTRIBUTE = os.environ["CARDS_LDAP_USER_ID_ATTRIBUTE"]
else:
  CARDS_LDAP_USER_ID_ATTRIBUTE = input("LDAP User ID Attribute: ")


form_data = {}
form_data['apply'] = 'true'
form_data['action'] = 'ajaxConfigManager'
form_data['$location'] = ''
form_data['provider.name'] = CARDS_LDAP_PROVIDER_NAME
form_data['host.name'] = CARDS_LDAP_HOSTNAME
form_data['host.port'] = CARDS_LDAP_PORT
form_data['host.ssl'] = CARDS_LDAP_SSL
form_data['host.tls'] = CARDS_LDAP_TLS
form_data['host.noCertCheck'] = CARDS_LDAP_NO_CERT_CHECK
form_data['bind.dn'] = CARDS_LDAP_BIND_DN
form_data['bind.password'] = CARDS_LDAP_BIND_PASSWORD
form_data['user.baseDN'] = CARDS_LDAP_USER_BASE_DN
form_data['user.objectclass'] = CARDS_LDAP_USER_OBJECT_CLASS
form_data['user.idAttribute'] = CARDS_LDAP_USER_ID_ATTRIBUTE

propertylist = []
propertylist.append('provider.name')
propertylist.append('host.name')
propertylist.append('host.port')
propertylist.append('host.ssl')
propertylist.append('host.tls')
propertylist.append('host.noCertCheck')
propertylist.append('bind.dn')
propertylist.append('bind.password')
propertylist.append('user.baseDN')
propertylist.append('user.objectclass')
propertylist.append('user.idAttribute')
form_data['propertylist'] = ','.join(propertylist)


config_req = requests.post(CARDS_URL + "/system/console/configMgr/org.apache.jackrabbit.oak.security.authentication.ldap.impl.LdapIdentityProvider", data=form_data, auth=HTTPBasicAuth('admin', ADMIN_PASSWORD))
if config_req.status_code != 200:
  print("Error while configuring LDAP")
  sys.exit(-1)
print("Configured LDAP")
