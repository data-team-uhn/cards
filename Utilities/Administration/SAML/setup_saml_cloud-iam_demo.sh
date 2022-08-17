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

# Check that we're using the correct samlKeystore.p12 file
sha256sum -c cloud-iam_demo_samlKeystore.p12.sha256sum || { echo "Invalid samlKeystore.p12 file. Please ensure that the correct samlKeystore.p12 file is in the CARDS root directory."; exit -1; }

python3 add_saml_sp_config.py --saml2IDPDestination https://lemur-15.cloud-iam.com/auth/realms/cards-saml-test/protocol/saml --saml2LogoutURL http://localhost:8080/ || { echo "Failed to add SAML SP configuration"; exit -1; }

echo "CARDS login via SAML now enabled for https://lemur-15.cloud-iam.com using the realm \"cards-saml-test\""
