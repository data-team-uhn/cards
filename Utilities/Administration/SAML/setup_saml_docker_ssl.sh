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

python3 create_service_user_mapping.py || { echo "Failed to create service user mapping"; exit -1; }
python3 create_saml_service_user.py || { echo "Failed to create saml service user"; exit -1; }
python3 configure_saml_service_user_permissions.py || { echo "Failed to configure saml service user permissions"; exit -1; }
python3 add_saml_sp_config.py --saml2LogoutURL https://localhost/ --entityID https://localhost/ || { echo "Failed to add SAML SP configuration"; exit -1; }

echo "CARDS login via SAML now enabled for localhost:8484 using the realm \"myrealm\""
