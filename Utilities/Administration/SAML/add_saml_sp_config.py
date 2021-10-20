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
import argparse
import requests
from requests.auth import HTTPBasicAuth

argparser = argparse.ArgumentParser()
argparser.add_argument('--entityID', help='SAML Entity ID for this Service Provider (SP) [default: http://localhost:8080/]', default='http://localhost:8080/')
argparser.add_argument('--acsPath', help='SAML Consumer Endpoint path [default: /sp/consumer]', default='/sp/consumer')
argparser.add_argument('--saml2userIDAttr', help='SAML claim to be used as the user identifier [default: urn:oid:1.2.840.113549.1.9.1]', default='urn:oid:1.2.840.113549.1.9.1')
argparser.add_argument('--saml2IDPDestination', help='The URL on the Identity Provider (IdP) that will provide the authentication screen [default: http://localhost:8484/auth/realms/myrealm/protocol/saml]', default='http://localhost:8484/auth/realms/myrealm/protocol/saml')
argparser.add_argument('--saml2LogoutURL', help='The URL to redirect the client to after logging out [default: http://localhost:8484/]', default='http://localhost:8484/')
argparser.add_argument('--jksFileLocation', help='The location of the keystore file containing the SAML decryption and verification keys [default: ./samlKeystore.p12]', default='./samlKeystore.p12')
argparser.add_argument('--idpCertAlias', help='Keystore identifier for the IdP verification certificate [default: idpsigningalias]', default='idpsigningalias')
argparser.add_argument('--spKeysAlias', help='Keystore identifier for the SAML IdP response decryption key [default: 1]', default='1')
argparser.add_argument('--prompt_keystore_password', help='Prompt for the keystore password instead of using the default "changeit" password', action='store_true')
args = argparser.parse_args()

CARDS_URL = "http://localhost:8080"
if "CARDS_URL" in os.environ:
  CARDS_URL = os.environ["CARDS_URL"].rstrip('/')

ADMIN_PASSWORD = "admin"
if "ADMIN_PASSWORD" in os.environ:
  ADMIN_PASSWORD = os.environ["ADMIN_PASSWORD"]

KEYSTORE_PASSWORD = "changeit"
if args.prompt_keystore_password:
  KEYSTORE_PASSWORD = input("Enter keystore password: ")

form_data = {}
form_data["apply"] = "true"
form_data["factoryPid"] = "org.apache.sling.auth.saml2.AuthenticationHandlerSAML2"
form_data["action"] = "ajaxConfigManager"
form_data["$location"] = ""
form_data["path"] = "/"
form_data["entityID"] = args.entityID
form_data["acsPath"] = args.acsPath
form_data["saml2userIDAttr"] = args.saml2userIDAttr
form_data["saml2userHome"] = "/home/users/saml"
form_data["saml2groupMembershipAttr"] = ""
form_data["syncAttrs"] = ""
form_data["saml2SessionAttr"] = "saml2AuthInfo"
form_data["saml2IDPDestination"] = args.saml2IDPDestination
form_data["saml2LogoutURL"] = args.saml2LogoutURL
form_data["saml2SPEnabled"] = ["true","false"]
form_data["saml2SPEncryptAndSign"] = ["true", "false"]
form_data["jksFileLocation"] = args.jksFileLocation
form_data["jksStorePassword"] = KEYSTORE_PASSWORD
form_data["idpCertAlias"] = args.idpCertAlias
form_data["spKeysAlias"] = args.spKeysAlias
form_data["spKeysPassword"] = KEYSTORE_PASSWORD
form_data["propertylist"] = "path,entityID,acsPath,saml2userIDAttr,saml2userHome,saml2groupMembershipAttr,syncAttrs,saml2SessionAttr,saml2IDPDestination,saml2LogoutURL,saml2SPEnabled,saml2SPEncryptAndSign,jksFileLocation,jksStorePassword,idpCertAlias,spKeysAlias,spKeysPassword"

r = requests.post(CARDS_URL + "/system/console/configMgr/[Temporary%20PID%20replaced%20by%20real%20PID%20upon%20save]", data=form_data, auth=HTTPBasicAuth('admin', ADMIN_PASSWORD))

if (r.status_code != 200):
  print("Error adding SAML as login method")
  sys.exit(-1)
print("Added SAML as login method")
