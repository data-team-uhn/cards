#!/bin/bash

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

echo "Generating the Service Provider (SP) keypair..."
openssl req -newkey rsa:4096 -nodes -keyout samlSPkey.pem -x509 -days 365 -out samlSPcert.pem
openssl pkcs12 -inkey samlSPkey.pem -in samlSPcert.pem -export -out samlKeystore.p12 || { echo "Failed to create initial keystore"; exit -1; }

echo "Please import $(realpath samlSPcert.pem) into the IdP"
echo "After importing $(realpath samlSPcert.pem), press any key to continue"
read

echo "Obtaining IdP's signing certificate..."
echo "-----BEGIN CERTIFICATE-----" > signingCert.pem
python3 print_idp_cert_from_url.py >> signingCert.pem || { echo "Failed to obtain IdP signing cert"; exit -1; }
echo "-----END CERTIFICATE-----" >> signingCert.pem

echo "Adding IdP's signing certificate to the keystore"
keytool -import -file signingCert.pem -keystore samlKeystore.p12 -alias idpsigningalias

echo "Done"
