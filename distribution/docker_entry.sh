#!/bin/sh

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

#If we are simply restarting...
[ -z $CARDS_RELOAD ] || WAIT_FOR_CARDSINIT=''

# If we have not explicitly specified the file system as the Oak Repo
# back-end for data storage, use MongoDB
STORAGE=tar
[ -z $OAK_FILESYSTEM ] && STORAGE=mongo

#If inside a docker-compose environment, wait for a signal...
[ -z $INSIDE_DOCKER_COMPOSE ] || (while true; do (echo "CARDS" | nc router 9999) && break; sleep 5; done)

#If (inside a docker-compose environment), we are supposed to wait for http://cardsinitial:8080/ to start
[ -z $WAIT_FOR_CARDSINIT ] || (while true; do (wget -S --spider http://cardsinitial:8080/ 2>&1 | grep 'HTTP/1.1 200 OK') && break; sleep 10; done)

PERMISSIONS="${PERMISSIONS:-open}"

PROJECT_ARTIFACTID=$1
PROJECT_VERSION=$2

#Parse the (legacy) ADDITIONAL_RUN_MODES environment variable and determine the features that need to be enabled
featureFlagString=""
if [[ "${CARDS_PROJECT}" == 'cards4care' ]]
then
  featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards4care/${CARDS_VERSION}/slingosgifeature"
elif [[ "${CARDS_PROJECT}" == 'cards4kids' ]]
then
  featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards4kids/${CARDS_VERSION}/slingosgifeature"
elif [[ "${CARDS_PROJECT}" == 'cards4lfs' ]]
then
  featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards4lfs/${CARDS_VERSION}/slingosgifeature"
elif [[ "${CARDS_PROJECT}" == 'cards4proms' ]]
then
  featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards4proms/${CARDS_VERSION}/slingosgifeature"
fi

legacyRunModes=$(echo $ADDITIONAL_RUN_MODES | tr "," "\n")
for legacyRunMode in $legacyRunModes
do
  #Perform the translation
  if [[ ${legacyRunMode} == 'oak_tar' ]]
  then
    STORAGE=tar
  elif [[ ${legacyRunMode} == 'oak_mongo' ]]
  then
    STORAGE=mongo
  elif [[ ${legacyRunMode} == 'dev' ]]
  then
    featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards/${CARDS_VERSION}/slingosgifeature/composum"
  elif [[ ${legacyRunMode} == 'cardiac_rehab' ]]
  then
    featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards4care/${CARDS_VERSION}/slingosgifeature"
  elif [[ ${legacyRunMode} == 'kids' ]]
  then
    featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards4kids/${CARDS_VERSION}/slingosgifeature"
  elif [[ ${legacyRunMode} == 'lfs' ]]
  then
    featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards4lfs/${CARDS_VERSION}/slingosgifeature"
  elif [[ ${legacyRunMode} == 'proms' ]]
  then
    featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards4proms/${CARDS_VERSION}/slingosgifeature"
  elif [[ ${legacyRunMode} == 'test' ]]
  then
    featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards-modules-test-forms/${CARDS_VERSION}/slingosgifeature"
  elif [[ ${legacyRunMode} == 'demo' ]]
  then
    featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards-modules-demo-banner/${CARDS_VERSION}/slingosgifeature,"
    featureFlagString="${featureFlagString}mvn:io.uhndata.cards/cards-modules-upgrade-marker/${CARDS_VERSION}/slingosgifeature,"
    featureFlagString="${featureFlagString}mvn:io.uhndata.cards/cards-dataentry/${CARDS_VERSION}/slingosgifeature/forms_demo"
  elif [[ ${legacyRunMode} == 'permissions_open' ]]
  then
    PERMISSIONS="open"
  elif [[ ${legacyRunMode} == 'permissions_trusted' ]]
  then
    PERMISSIONS="trusted"
  elif [[ ${legacyRunMode} == 'permissions_ownership' ]]
  then
    PERMISSIONS="ownership"
  fi
done

echo "STORAGE = $STORAGE"
echo "DEV = $DEV"
echo "DEBUG = $DEBUG"
echo "PERMISSIONS = $PERMISSIONS"
echo "ADDITIONAL_RUN_MODES = $ADDITIONAL_RUN_MODES"
echo "PROJECT_ARTIFACTID = $PROJECT_ARTIFACTID"
echo "PROJECT_VERSION = $PROJECT_VERSION"

#Execute the volume_mounted_init.sh script if it is present
[ -e /volume_mounted_init.sh ] && /volume_mounted_init.sh

java -Djdk.xml.entityExpansionLimit=0 ${DEBUG:+ -Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005} -jar org.apache.sling.feature.launcher.jar -f ./${PROJECT_ARTIFACTID}-${PROJECT_VERSION}-core_${STORAGE}_far.far -f mvn:io.uhndata.cards/cards-dataentry/${CARDS_VERSION}/slingosgifeature/permissions_${PERMISSIONS}${featureFlagString}
