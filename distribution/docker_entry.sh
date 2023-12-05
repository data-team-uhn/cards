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

#If SAML is enabled, default to "trusted" permissions. Otherwise, default to whatever is specified by the project via sling-features.json.
if [[ "$SAML_AUTH_ENABLED" == "true" ]]
then
  PERMISSIONS="${PERMISSIONS:-trusted}"
fi

CARDS_ARTIFACTID=$1
CARDS_VERSION=$2

if [ -z $PROJECT_VERSION ]
then
  PROJECT_VERSION=$CARDS_VERSION
fi

VALID_PROJECT_NAMES="||cards4kids|cards4lfs|cards4proms|cards4prems|cards4heracles|"
[ -e /external_project/project_code.txt ] && VALID_PROJECT_NAMES="${VALID_PROJECT_NAMES}$(cat /external_project/project_code.txt | head -n 1 | tr -d '\n')|"
echo "${VALID_PROJECT_NAMES}" | grep -q "|${PROJECT_NAME}|" || { echo "Invalid project specified - defaulting to generic CARDS."; unset PROJECT_NAME; }

featureFlagString=""
if [ ! -z $PROJECT_NAME ] && [ ! -z $PROJECT_VERSION ]
then
  featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/${PROJECT_NAME}/${PROJECT_VERSION}/slingosgifeature"
fi

if [ ! -z $DEV ]
then
  featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards/${CARDS_VERSION}/slingosgifeature/composum"
fi

if [ ! -z $DEMO ]
then
  featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards-modules-demo-banner/${CARDS_VERSION}/slingosgifeature,"
  featureFlagString="${featureFlagString}mvn:io.uhndata.cards/cards-modules-upgrade-marker/${CARDS_VERSION}/slingosgifeature,"
  featureFlagString="${featureFlagString}mvn:io.uhndata.cards/cards-dataentry/${CARDS_VERSION}/slingosgifeature/forms_demo"
else
  if [ ! -z $DEMO_BANNER ]
  then
    featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards-modules-demo-banner/${CARDS_VERSION}/slingosgifeature"
  fi
fi

if [ ! -z $ENABLE_TEST_FEATURES ]
then
  featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards-modules-test-forms/${CARDS_VERSION}/slingosgifeature"
fi

if [ ! -z $SAML_CLOUD_IAM_DEMO ]
then
  if [[ ${BEHIND_SSL_PROXY} == 'true' ]]
  then
    featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards-cloud-iam-demo-saml-support-ssl/${CARDS_VERSION}/slingosgifeature"
  else
    featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards-cloud-iam-demo-saml-support/${CARDS_VERSION}/slingosgifeature"
  fi
fi

if [ ! -z $ADDITIONAL_SLING_FEATURES ]
then
  featureFlagString="$featureFlagString -f ${ADDITIONAL_SLING_FEATURES@P}"
fi

# Read /sling-features.json and enable the features required for this project
PROJECT_REQUIRED_FEATURES=$(CARDS_VERSION=${CARDS_VERSION} PROJECT_NAME=${PROJECT_NAME} PROJECT_VERSION=${PROJECT_VERSION} PERMISSIONS=${PERMISSIONS} python3 /get_project_dependency_features.py /sling-features.json)
if [ ! -z $PROJECT_REQUIRED_FEATURES ]
then
  featureFlagString="$featureFlagString -f $PROJECT_REQUIRED_FEATURES"
fi

echo "STORAGE = $STORAGE"
echo "DEV = $DEV"
echo "DEMO = $DEMO"
echo "ENABLE_TEST_FEATURES = $ENABLE_TEST_FEATURES"
echo "DEBUG = $DEBUG"
echo "PERMISSIONS = $PERMISSIONS"
echo "ADDITIONAL_RUN_MODES = $ADDITIONAL_RUN_MODES"
echo "ADDITIONAL_SLING_FEATURES = $ADDITIONAL_SLING_FEATURES"
echo "CARDS_ARTIFACTID = $CARDS_ARTIFACTID"
echo "CARDS_VERSION = $CARDS_VERSION"
echo "PROJECT_NAME = $PROJECT_NAME"
echo "PROJECT_VERSION = $PROJECT_VERSION"

#Are we using an external MongoDB service for data storage?
EXT_MONGO_VARIABLES=""
if [ ! -z $EXTERNAL_MONGO_URI ]
then
  AUTH_EXTERNAL_MONGO_URI=$EXTERNAL_MONGO_URI
  if [ ! -z $MONGO_AUTH ]
  then
    AUTH_EXTERNAL_MONGO_URI="$MONGO_AUTH@$AUTH_EXTERNAL_MONGO_URI"
  fi
  if [ ! -z $CUSTOM_MONGO_DB_NAME ]
  then
    EXT_MONGO_VARIABLES="$EXT_MONGO_VARIABLES -V mongo.db=$CUSTOM_MONGO_DB_NAME"
  fi
  EXT_MONGO_VARIABLES="$EXT_MONGO_VARIABLES -V mongo.uri=$AUTH_EXTERNAL_MONGO_URI"
fi

SMTPS_VARIABLES=""
if [ ! -z $SMTPS_HOST ]
then
  SMTPS_VARIABLES="$SMTPS_VARIABLES -V emailnotifications.smtps.host=$SMTPS_HOST"
fi

#Should the SMTPS OSGi bundle be enabled?
if [[ "$SMTPS_ENABLED" == "true" ]]
then
  featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards-email-notifications/${CARDS_VERSION}/slingosgifeature"
fi

#Should the SAML OSGi bundle be enabled?
if [[ "$SAML_AUTH_ENABLED" == "true" ]]
then
  featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards-saml-support/${CARDS_VERSION}/slingosgifeature/base -C io.dropwizard.metrics:metrics-core:ALL -f mvn:io.uhndata.cards/cards-fetch-requires-saml-login/${CARDS_VERSION}/slingosgifeature"
fi

#Should the scheduled-csv-export module be loaded?
if [[ "$CSV_EXPORT_ENABLED" == "true" ]]
then
  featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards-scheduled-csv-export/${CARDS_VERSION}/slingosgifeature"
fi

#Should the clarity-integration module be loaded?
if [[ "$CLARITY_IMPORT_ENABLED" == "true" ]]
then
  featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards-clarity-integration/${CARDS_VERSION}/slingosgifeature"
fi

#Should the cards-slack-notifications module be loaded?
if [[ "$SLACK_NOTIFICATIONS_ENABLED" == "true" ]]
then
  featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards-slack-notifications/${CARDS_VERSION}/slingosgifeature"
fi

if [[ "$SMTPS_LOCALHOST_PROXY" == "true" ]]
then
  keytool -import -trustcacerts -file /etc/cert/smtps_certificate.crt -keystore /etc/ssl/certs/java/cacerts -keypass changeit -storepass changeit -noprompt
  SMTPS_VARIABLES="$SMTPS_VARIABLES -V emailnotifications.smtps.checkserveridentity=false"
fi

if [[ "$SMTPS_LOCAL_TEST_CONTAINER" == "true" ]]
then
  SMTPS_VARIABLES="$SMTPS_VARIABLES -V emailnotifications.smtps.checkserveridentity=false"
  SMTPS_VARIABLES="$SMTPS_VARIABLES -V emailnotifications.smtps.host=smtps_test_container"
  SMTPS_VARIABLES="$SMTPS_VARIABLES -V emailnotifications.smtps.port=465"
fi

#Load all the SSL certs under SSL_CONFIG/cards_certs into Java's trusted CA keystore
for CERT_FILE in $(find /load_certs -type f -name "*.pem" -o -name "*.crt")
do
  echo "Adding $CERT_FILE to Java's trusted CA keystore"
  keytool -import -trustcacerts -file $CERT_FILE -keystore /etc/ssl/certs/java/cacerts -keypass changeit -storepass changeit -noprompt
done

#Execute the volume_mounted_init.sh script if it is present
[ -e /volume_mounted_init.sh ] && /volume_mounted_init.sh

java -Djdk.xml.entityExpansionLimit=0 ${CARDS_JAVA_MEMORY_LIMIT_MB:+ -Xmx${CARDS_JAVA_MEMORY_LIMIT_MB}m} ${DEBUG:+ -Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=*:5005} -jar org.apache.sling.feature.launcher.jar -p .cards-data -u "file://$(realpath ${HOME}/.m2/repository),file://$(realpath ${HOME}/.cards-generic-m2/repository),https://nexus.phenotips.org/nexus/content/groups/public,https://repo.maven.apache.org/maven2,https://repository.apache.org/content/groups/snapshots" -f ./${CARDS_ARTIFACTID}-${CARDS_VERSION}-core_${STORAGE}_far.far${EXT_MONGO_VARIABLES}${SMTPS_VARIABLES}${featureFlagString}
