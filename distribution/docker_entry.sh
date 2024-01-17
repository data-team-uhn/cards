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

#If SAML is enabled, default to "trusted" permissions. Otherwise, default to "open" permissions.
if [[ "$SAML_AUTH_ENABLED" == "true" ]]
then
  PERMISSIONS="${PERMISSIONS:-trusted}"
else
  PERMISSIONS="${PERMISSIONS:-open}"
fi

PROJECT_ARTIFACTID=$1
PROJECT_VERSION=$2

VALID_CARDS_PROJECTS="||cards4kids|cards4lfs|cards4proms|cards4prems|cards4heracles|"
echo "${VALID_CARDS_PROJECTS}" | grep -q "|${CARDS_PROJECT}|" || { echo "Invalid project specified - defaulting to generic CARDS."; unset CARDS_PROJECT; }

featureFlagString=""
if [ ! -z $CARDS_PROJECT ]
then
  featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/${CARDS_PROJECT}/${PROJECT_VERSION}/slingosgifeature"
fi

[[ "${CARDS_PROJECT}" == 'cards4proms' || "${CARDS_PROJECT}" == 'cards4prems' ]] && SMTPS_ENABLED="true"

[[ "${CARDS_PROJECT}" == 'cards4prems' ]] && CSV_EXPORT_ENABLED="true"
[[ "${CARDS_PROJECT}" == 'cards4proms' || "${CARDS_PROJECT}" == 'cards4prems' ]] && CLARITY_IMPORT_ENABLED="true"

if [ ! -z $DEV ]
then
  featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards/${PROJECT_VERSION}/slingosgifeature/composum"
fi

if [ ! -z $DEMO ]
then
  featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards-modules-demo-banner/${PROJECT_VERSION}/slingosgifeature,"
  featureFlagString="${featureFlagString}mvn:io.uhndata.cards/cards-modules-upgrade-marker/${PROJECT_VERSION}/slingosgifeature,"
  featureFlagString="${featureFlagString}mvn:io.uhndata.cards/cards-dataentry/${PROJECT_VERSION}/slingosgifeature/forms_demo"
else
  if [ ! -z $DEMO_BANNER ]
  then
    featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards-modules-demo-banner/${PROJECT_VERSION}/slingosgifeature"
  fi
fi

if [ ! -z $ENABLE_TEST_FEATURES ]
then
  featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards-modules-test-forms/${PROJECT_VERSION}/slingosgifeature"
fi

if [ ! -z $SAML_CLOUD_IAM_DEMO ]
then
  if [[ ${BEHIND_SSL_PROXY} == 'true' ]]
  then
    featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards-cloud-iam-demo-saml-support-ssl/${PROJECT_VERSION}/slingosgifeature"
  else
    featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards-cloud-iam-demo-saml-support/${PROJECT_VERSION}/slingosgifeature"
  fi
fi

if [ ! -z $ADDITIONAL_SLING_FEATURES ]
then
  featureFlagString="$featureFlagString -f $ADDITIONAL_SLING_FEATURES"
fi

#Parse the (legacy) ADDITIONAL_RUN_MODES environment variable and determine the features that need to be enabled
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
    featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards/${PROJECT_VERSION}/slingosgifeature/composum"
  elif [[ ${legacyRunMode} == 'kids' ]]
  then
    featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards4kids/${PROJECT_VERSION}/slingosgifeature"
  elif [[ ${legacyRunMode} == 'lfs' ]]
  then
    featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards4lfs/${PROJECT_VERSION}/slingosgifeature"
  elif [[ ${legacyRunMode} == 'proms' ]]
  then
    featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards4proms/${PROJECT_VERSION}/slingosgifeature"
    SMTPS_ENABLED="true"
  elif [[ ${legacyRunMode} == 'test' ]]
  then
    featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards-modules-test-forms/${PROJECT_VERSION}/slingosgifeature"
  elif [[ ${legacyRunMode} == 'demo' ]]
  then
    featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards-modules-demo-banner/${PROJECT_VERSION}/slingosgifeature,"
    featureFlagString="${featureFlagString}mvn:io.uhndata.cards/cards-modules-upgrade-marker/${PROJECT_VERSION}/slingosgifeature,"
    featureFlagString="${featureFlagString}mvn:io.uhndata.cards/cards-dataentry/${PROJECT_VERSION}/slingosgifeature/forms_demo"
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

# Read /sling-features.json and enable the features required for this project
PROJECT_REQUIRED_FEATURES=$(PROJECT_VERSION=${PROJECT_VERSION} PERMISSIONS=${PERMISSIONS} python3 /get_project_dependency_features.py /sling-features.json)
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
echo "PROJECT_ARTIFACTID = $PROJECT_ARTIFACTID"
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
  featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards-email-notifications/${PROJECT_VERSION}/slingosgifeature"
fi

#Should the SAML OSGi bundle be enabled?
if [[ "$SAML_AUTH_ENABLED" == "true" ]]
then
  featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards-saml-support/${PROJECT_VERSION}/slingosgifeature/base -C io.dropwizard.metrics:metrics-core:ALL -f mvn:io.uhndata.cards/cards-fetch-requires-saml-login/${PROJECT_VERSION}/slingosgifeature"
fi

#Should the scheduled-csv-export module be loaded?
if [[ "$CSV_EXPORT_ENABLED" == "true" ]]
then
  featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards-scheduled-csv-export/${PROJECT_VERSION}/slingosgifeature"
fi

#Should the clarity-integration module be loaded?
if [[ "$CLARITY_IMPORT_ENABLED" == "true" ]]
then
  featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards-clarity-integration/${PROJECT_VERSION}/slingosgifeature"
fi

#Should the cards-slack-notifications module be loaded?
if [[ "$SLACK_NOTIFICATIONS_ENABLED" == "true" ]]
then
  featureFlagString="$featureFlagString -f mvn:io.uhndata.cards/cards-slack-notifications/${PROJECT_VERSION}/slingosgifeature"
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

java -Djdk.xml.entityExpansionLimit=0 ${CARDS_JAVA_MEMORY_LIMIT_MB:+ -Xmx${CARDS_JAVA_MEMORY_LIMIT_MB}m} ${DEBUG:+ -Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=*:5005} -jar org.apache.sling.feature.launcher.jar -p .cards-data -u "file://$(realpath ${HOME}/.m2/repository),https://nexus.phenotips.org/nexus/content/groups/public,https://repo.maven.apache.org/maven2,https://repository.apache.org/content/groups/snapshots" -f ./${PROJECT_ARTIFACTID}-${PROJECT_VERSION}-core_${STORAGE}_far.far${EXT_MONGO_VARIABLES}${SMTPS_VARIABLES}${featureFlagString}
