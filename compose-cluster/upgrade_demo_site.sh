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

# CARDS_URL should point to a HTTP URL that gives direct access to
# Apache Sling which therefore can be used for configuring (eg. setting
# passwords) before the reverse proxy is started and the service is made
# available.
if [ -z $CARDS_URL ]
then
  echo "CARDS_URL environment variable must be set"
  exit -1
fi

# Check if an upgrade is available. Exit if an upgrade is not available.
# If the `value` field for the JCR node `/UpgradeMarker` is set to true
# and `upgradeVersion` field for JCR node `/UpgradeMarker` is a legal
# version string, upgrade to that tagged version
echo "Checking if an upgraded version is available..."
CARDS_NEW_VERSION=$(python3 check_upgrade_version.py) || exit
echo "...Looks like an upgraded version is available."

#Checkout the appropriate version tag
cd $PROJECT_ROOT
git fetch --all || exit -1
git fetch --tags || exit -1
git checkout cards-$CARDS_NEW_VERSION || exit -1

#Build the CARDS image from the most up-to-date released source code
mvn clean install || exit -1

#Stop CARDS and cleanup
cd compose-cluster
docker-compose down
docker-compose rm
docker volume prune -f

#Upgrade the version of CARDS specified in the docker-compose.yml file
python3 upgrade_cards_image.py --cards_docker_tag $CARDS_NEW_VERSION

#Start the new CARDS Docker image
docker-compose up -d cardsinitial

#Wait for CARDS to start
while true
do
  echo "Waiting for CARDS to start"
  curl --fail $CARDS_URL/system/sling/info.sessionInfo.json > /dev/null 2> /dev/null && break
  sleep 5
done

#Set the `admin` and `github` Sling user passwords
cd $PROJECT_ROOT/Utilities/Administration
cat ~/.cards_credentials/github_password.txt | python3 set_sling_password.py --user github || exit -1
cat ~/.cards_credentials/admin_password.txt | python3 set_sling_password.py || exit -1

#Start the reverse proxy so that the demo becomes publicly available
cd $PROJECT_ROOT/compose-cluster
docker-compose up -d
