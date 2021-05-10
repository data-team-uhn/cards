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

PROJECT_ROOT=$(realpath ../)

#Generate the docker-compose environment
cd $PROJECT_ROOT/compose-cluster
echo "ADDITIONAL_RUN_MODES=cardiac_rehab,dev" > custom_env.env
python3 generate_compose_yaml.py --oak_filesystem --sling_admin_port 8081 --custom_env_file custom_env.env
docker-compose build
docker-compose up -d lfsinitial

#Wait for it to start
while true
do
  echo "Waiting for CARDS to start"
  curl --fail http://localhost:8081/system/sling/info.sessionInfo.json && break
  sleep 5
done
echo ""

#Set the admin password
cd $PROJECT_ROOT/Utilities/Administration
export CARDS_URL=http://localhost:8081
if [ -z "$CARDS_DEPLOYMENT_ADMIN_PASSWORD" ]
then
  echo "CARDS_DEPLOYMENT_ADMIN_PASSWORD unspecified, generating a random one"
  export CARDS_DEPLOYMENT_ADMIN_PASSWORD=$(openssl rand -hex 8)
  echo ""
  echo "**********************************************************"
  echo -n "Admin password for this deployment is: "
  printenv CARDS_DEPLOYMENT_ADMIN_PASSWORD
  echo "**********************************************************"
  echo ""
fi
printenv CARDS_DEPLOYMENT_ADMIN_PASSWORD | python3 set_sling_password.py

#Start up everything else
cd $PROJECT_ROOT/compose-cluster
docker-compose up -d

#Ready to go
echo "cards4care_local deployment is now ready at port 8080"
