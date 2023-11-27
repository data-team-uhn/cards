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

BIND_TESTS=2
BIND_TEST_SPACING=30

TERMINAL_NOCOLOR='\033[0m'
TERMINAL_RED='\033[0;31m'
TERMINAL_GREEN='\033[0;32m'
TERMINAL_YELLOW='\033[0;33m'

#CTRL+C should stop everything started by this script
trap ctrl_c INT
function ctrl_c() {
  if [ ! -z $KEYCLOAK_HEADERMOD_HTTP_PROXY_PID ]
  then
    echo "Shutting down keycloak_headermod_http_proxy.js"
    kill $KEYCLOAK_HEADERMOD_HTTP_PROXY_PID
    wait $KEYCLOAK_HEADERMOD_HTTP_PROXY_PID
  fi
  echo "Shutting down CARDS"
  kill $CARDS_PID
  wait $CARDS_PID
  exit
}

function check_cards_running() {
  jobs -pr | grep '^'$CARDS_PID'$' > /dev/null
}

function print_length_of() {
  local i
  for i in `seq 1 $(echo -n "$1" | wc -c)`
  do
    echo -n "$2"
  done
  #Print extra
  for i in `seq 1 $3`
  do
    echo -n "$2"
  done
}

function print_pad_right() {
  printf "%-$2s" "$1"
}

function get_error_log_last_modified() {
  echo "$((stat --format="%.Y" .cards-data/logs/error.log 2>/dev/null) || echo 0.00)"
}

function handle_missing_sling_commons_crypto_warning() {
  echo -e "${TERMINAL_YELLOW}*************************************************************************${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_YELLOW}*                                                                       *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_YELLOW}*   The SLING_COMMONS_CRYPTO_PASSWORD enviroment variable is missing.   *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_YELLOW}*   Using the default value of 'password'.                              *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_YELLOW}*                                                                       *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_YELLOW}*************************************************************************${TERMINAL_NOCOLOR}"
}

function handle_missing_mailcap_fail() {
  echo -e "${TERMINAL_RED}********************************************************************************${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}*                                                                              *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}*   The file ~/.mailcap is missing. Exiting.                                   *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}*   You can create ~/.mailcap by running: cp distribution/mailcap ~/.mailcap   *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}*                                                                              *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}********************************************************************************${TERMINAL_NOCOLOR}"
  exit -1
}

function warn_different_mailcap() {
  echo -e "${TERMINAL_YELLOW}********************************************************************${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_YELLOW}*                                                                  *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_YELLOW}*    Warning: The file ~/.mailcap differs from what is expected.   *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_YELLOW}*    Sending emails may not work properly!                         *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_YELLOW}*                                                                  *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_YELLOW}********************************************************************${TERMINAL_NOCOLOR}"
}

function handle_cards_java_fail() {
  echo -e "${TERMINAL_RED}**********************************************$(print_length_of $BIND_PORT '*' 4)${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}*                                             $(print_length_of $BIND_PORT ' ' 3)*${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}*   The CARDS Java process has failed at port ${BIND_PORT}   *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}*                                             $(print_length_of $BIND_PORT ' ' 3)*${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}**********************************************$(print_length_of $BIND_PORT '*' 4)${TERMINAL_NOCOLOR}"
  exit -1
}

function handle_tcp_bind_fail() {
  echo -e "${TERMINAL_RED}*******************************$(print_length_of $BIND_PORT '*' 4)${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}*                              $(print_length_of $BIND_PORT ' ' 3)*${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}*   Unable to bind to TCP port ${BIND_PORT}   *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}*                              $(print_length_of $BIND_PORT ' ' 3)*${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}*******************************$(print_length_of $BIND_PORT '*' 4)${TERMINAL_NOCOLOR}"
  exit -1
}

function handle_tcp_bind_ok_optimal() {
  echo -e "${TERMINAL_GREEN}*****************************${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_GREEN}*                           *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_GREEN}*   CARDS Socket BIND: OK   *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_GREEN}*                           *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_GREEN}*****************************${TERMINAL_NOCOLOR}"
}

function handle_tcp_bind_ok_suboptimal() {
  echo -e "${TERMINAL_YELLOW}*********************************************************${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_YELLOW}*                                                       *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_YELLOW}*   CARDS Socket BIND: OK - used suboptimal bind test   *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_YELLOW}*                                                       *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_YELLOW}*********************************************************${TERMINAL_NOCOLOR}"
}

function message_bioportal_apikey_missing() {
  echo -e "${TERMINAL_RED}********************************************************************${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}*                                                                  *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}* BIOPORTAL_APIKEY not specified, skipping HANCESTRO installation. *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}*                                                                  *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}********************************************************************${TERMINAL_NOCOLOR}"
}

function message_hancestro_install_ok() {
  echo -e "${TERMINAL_GREEN}***********************${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_GREEN}*                     *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_GREEN}* Installed HANCESTRO *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_GREEN}*                     *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_GREEN}***********************${TERMINAL_NOCOLOR}"
}

function message_hancestro_install_fail() {
  echo -e "${TERMINAL_RED}****************************${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}*                          *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}* HANCESTRO install failed *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}*                          *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}****************************${TERMINAL_NOCOLOR}"
}

function message_sha256_cloud_iam_ok() {
  echo -e "${TERMINAL_GREEN}****************************************************${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_GREEN}*                                                  *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_GREEN}* Setup Cloud-IAM.com Demo as a SAML IdP for CARDS *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_GREEN}*                                                  *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_GREEN}****************************************************${TERMINAL_NOCOLOR}"
}

function message_sha256_cloud_iam_error() {
  echo -e "${TERMINAL_YELLOW}********************************************************************${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_YELLOW}*                                                                  *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_YELLOW}* Invalid Sha256 hash for samlKeystore.p12 for Cloud-IAM.com demo. *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_YELLOW}* SAML authentication via Cloud-IAM.com IdP may not work.          *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_YELLOW}*                                                                  *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_YELLOW}********************************************************************${TERMINAL_NOCOLOR}"
}

function message_saml_proxy_port_conflict_fail() {
  echo -e "${TERMINAL_RED}***************************************************************************************${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}*                                                                                     *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}* Error: CARDS and keycloak_headermod_http_proxy.js cannot be bound to the same port. *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}*                                                                                     *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}***************************************************************************************${TERMINAL_NOCOLOR}"
}

function message_started_cards() {
  if [ -z $KEYCLOAK_HEADERMOD_HTTP_PROXY_PID ]
  then
    echo -e "${TERMINAL_GREEN}**************************$(print_length_of $BIND_PORT '*' 4)${TERMINAL_NOCOLOR}"
    echo -e "${TERMINAL_GREEN}*                         $(print_length_of $BIND_PORT ' ' 3)*${TERMINAL_NOCOLOR}"
    echo -e "${TERMINAL_GREEN}*   Started CARDS at port ${BIND_PORT}   *${TERMINAL_NOCOLOR}"
    echo -e "${TERMINAL_GREEN}*                         $(print_length_of $BIND_PORT ' ' 3)*${TERMINAL_NOCOLOR}"
    echo -e "${TERMINAL_GREEN}**************************$(print_length_of $BIND_PORT '*' 4)${TERMINAL_NOCOLOR}"
  else
    echo -e "${TERMINAL_GREEN}***************************************************${TERMINAL_NOCOLOR}"
    echo -e "${TERMINAL_GREEN}*                                                 *${TERMINAL_NOCOLOR}"
    echo -e "${TERMINAL_GREEN}*   Started CARDS at port $(print_pad_right ${BIND_PORT} 21)   *${TERMINAL_NOCOLOR}"
    echo -e "${TERMINAL_GREEN}*   Use port ${HEADERMOD_PROXY_LISTEN_PORT} for SAML + local Sling login.   *${TERMINAL_NOCOLOR}"
    echo -e "${TERMINAL_GREEN}*                                                 *${TERMINAL_NOCOLOR}"
    echo -e "${TERMINAL_GREEN}***************************************************${TERMINAL_NOCOLOR}"
  fi
}

function message_connect_jdb() {
  echo -e "${TERMINAL_YELLOW}******************************************************************${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_YELLOW}*                                                                *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_YELLOW}* Please connect JDB to localhost:5005 to continue with startup. *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_YELLOW}* jdb -attach 5005                                               *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_YELLOW}*                                                                *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_YELLOW}******************************************************************${TERMINAL_NOCOLOR}"
}

function get_cards_version() {
  CARDS_VERSION=$(cat pom.xml | grep --max-count=1 '^  <version>' | cut '-d>' -f2 | cut '-d<' -f1)
  echo CARDS_VERSION $CARDS_VERSION
}

#Determine the port that CARDS is to bind to
BIND_PORT=$(ARGUMENT_KEY='-p' ARGUMENT_DEFAULT='8080' python3 Utilities/HostConfig/argparse_bash.py $@)
export CARDS_URL="http://localhost:${BIND_PORT}"

#Get any specified runModes
EXPR='{"operation": "includes", "key": "sling.run.modes", "val": "test"}' python3 \
  Utilities/HostConfig/java_property_parser.py $@ && RUNMODE_TEST=true || RUNMODE_TEST=false

#Check if the psutil Python module is installed
python3 -c 'import psutil' 2>/dev/null && PSUTIL_INSTALLED=true || PSUTIL_INSTALLED=false

#If we are in WSL, psutil will not work, therefore act as if it is not installed
python3 -c 'import os; import sys; sys.exit(1 * ("Microsoft" not in os.uname().release))' && PSUTIL_INSTALLED=false

#If psutil is not installed, simply check if BIND_PORT is available now,
# and therefore will likely be available in the very near future
if [ $PSUTIL_INSTALLED = false ]
then
  python3 Utilities/HostConfig/check_tcp_available.py --tcp_port $BIND_PORT || handle_tcp_bind_fail
fi

# Filter the parameters to allow a less verbose start command, like `-p` to specify the port, or using `VERSION` to refer to the current version.
declare -a ARGS=("$@")
# Unset has strange effect on arrays, it leaves holes that somehow don't count towards the length of the array, so we must manually keep track of the index of the last element.
declare -i ARGS_LENGTH=${#ARGS[@]}
# Storage engine: default is TAR storage, allow switching to Mongo
declare OAK_STORAGE="tar"
# Permissions scheme: default is open, allow switching to something else
declare PERMISSIONS="open"
declare PERMISSIONS_EXPLICITLY_SET="false"
# Are we using the Cloud-IAM.com Keycloak demo instance?
declare CLOUD_IAM_DEMO="false"
# Is SAML authentication enabled?
declare SAML_IN_USE="false"
# Should any flags be passed to Java to enable debugging with JDB?
declare JAVA_DEBUGGING_FLAGS=""
get_cards_version

for ((i=0; i<${ARGS_LENGTH}; ++i));
do
  if [[ ${ARGS[$i]} == '-p' || ${ARGS[$i]} == '--port' ]]
  then
    # The port was already extracted, skip over -p and the port number
    unset ARGS[$i]
    i=${i}+1
    unset ARGS[$i]
  elif [[ ${ARGS[$i]} == '-P' || ${ARGS[$i]} == '--project' ]]
  then
    ARGS[$i]='-f'
    i=${i}+1
    PROJECTS=${ARGS[$i]//,/ }
    ARGS[$i]=''
    for PROJECT in $PROJECTS
    do
      # Support both "cards4project" and just "project": make sure the PROJECT starts with "cards4"
      PROJECT="cards4${PROJECT#cards4}"
      ARGS[$i]=${ARGS[$i]},mvn:io.uhndata.cards/${PROJECT}/${CARDS_VERSION}/slingosgifeature
      if [[ ${PROJECT} == 'cards4proms' || ${PROJECT} == 'cards4prems' ]]
      then
        # cards4proms requires the email module, make sure it's enabled
        ARGS[$ARGS_LENGTH]=-f
        ARGS_LENGTH=${ARGS_LENGTH}+1
        ARGS[$ARGS_LENGTH]=mvn:io.uhndata.cards/cards-email-notifications/${CARDS_VERSION}/slingosgifeature
        ARGS_LENGTH=${ARGS_LENGTH}+1

        # cards4proms requires the clarity-integration module, make sure it's enabled
        ARGS[$ARGS_LENGTH]=-f
        ARGS_LENGTH=${ARGS_LENGTH}+1
        ARGS[$ARGS_LENGTH]=mvn:io.uhndata.cards/cards-clarity-integration/${CARDS_VERSION}/slingosgifeature
        ARGS_LENGTH=${ARGS_LENGTH}+1

        # cards4proms uses the Trusted permission scheme by default
        if [[ ${PERMISSIONS_EXPLICITLY_SET} == 'false' ]]
        then
          PERMISSIONS="trusted"
        fi
      fi
      if [[ ${PROJECT} == 'cards4prems' ]]
      then
        # cards4prems requires the scheduled-csv-export module, make sure it's enabled
        ARGS[$ARGS_LENGTH]=-f
        ARGS_LENGTH=${ARGS_LENGTH}+1
        ARGS[$ARGS_LENGTH]=mvn:io.uhndata.cards/cards-scheduled-csv-export/${CARDS_VERSION}/slingosgifeature
        ARGS_LENGTH=${ARGS_LENGTH}+1

        # cards4prems requires the clarity-integration module, make sure it's enabled
        ARGS[$ARGS_LENGTH]=-f
        ARGS_LENGTH=${ARGS_LENGTH}+1
        ARGS[$ARGS_LENGTH]=mvn:io.uhndata.cards/cards-clarity-integration/${CARDS_VERSION}/slingosgifeature
        ARGS_LENGTH=${ARGS_LENGTH}+1
      fi
    done
    ARGS[$i]=${ARGS[$i]#,}
  elif [[ ${ARGS[$i]} == '--permissions' ]]
  then
    PERMISSIONS_EXPLICITLY_SET="true"
    unset ARGS[$i]
    i=${i}+1
    PERMISSIONS=${ARGS[$i]}
    unset ARGS[$i]
  elif [[ ${ARGS[$i]} == '--clarity' ]]
  then
    unset ARGS[$i]
    ARGS[$ARGS_LENGTH]=-f
    ARGS_LENGTH=${ARGS_LENGTH}+1
    ARGS[$ARGS_LENGTH]=mvn:io.uhndata.cards/cards-clarity-integration/${CARDS_VERSION}/slingosgifeature
    ARGS_LENGTH=${ARGS_LENGTH}+1
  elif [[ ${ARGS[$i]} == '--mongo' ]]
  then
    unset ARGS[$i]
    OAK_STORAGE="mongo"
  elif [[ ${ARGS[$i]} == '--dev' ]]
  then
    unset ARGS[$i]
    ARGS[$ARGS_LENGTH]=-f
    ARGS_LENGTH=${ARGS_LENGTH}+1
    ARGS[$ARGS_LENGTH]=mvn:io.uhndata.cards/cards/${CARDS_VERSION}/slingosgifeature/composum
    ARGS_LENGTH=${ARGS_LENGTH}+1
  elif [[ ${ARGS[$i]} == '--debug' ]]
  then
    unset ARGS[$i]
    JAVA_DEBUGGING_FLAGS="-Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005"
  elif [[ ${ARGS[$i]} == '--demo' ]]
  then
    unset ARGS[$i]
    ARGS[$ARGS_LENGTH]=-f
    ARGS_LENGTH=${ARGS_LENGTH}+1
    ARGS[$ARGS_LENGTH]=mvn:io.uhndata.cards/cards-modules-demo-banner/${CARDS_VERSION}/slingosgifeature,mvn:io.uhndata.cards/cards-modules-upgrade-marker/${CARDS_VERSION}/slingosgifeature,mvn:io.uhndata.cards/cards-dataentry/${CARDS_VERSION}/slingosgifeature/forms_demo
    ARGS_LENGTH=${ARGS_LENGTH}+1
  elif [[ ${ARGS[$i]} == '--test' ]]
  then
    RUNMODE_TEST=true
    unset ARGS[$i]
    ARGS[$ARGS_LENGTH]=-f
    ARGS_LENGTH=${ARGS_LENGTH}+1
    ARGS[$ARGS_LENGTH]=mvn:io.uhndata.cards/cards-modules-test-forms/${CARDS_VERSION}/slingosgifeature
    ARGS_LENGTH=${ARGS_LENGTH}+1
  elif [[ ${ARGS[$i]} == '--csv_export' ]]
  then
    unset ARGS[$i]
    ARGS[$ARGS_LENGTH]=-f
    ARGS_LENGTH=${ARGS_LENGTH}+1
    ARGS[$ARGS_LENGTH]=mvn:io.uhndata.cards/cards-scheduled-csv-export/${CARDS_VERSION}/slingosgifeature
    ARGS_LENGTH=${ARGS_LENGTH}+1
  elif [[ ${ARGS[$i]} == '--saml' ]]
  then
    if [[ ${PERMISSIONS_EXPLICITLY_SET} == 'false' ]]
    then
      PERMISSIONS="trusted"
    fi
    SAML_IN_USE="true"
    unset ARGS[$i]
    ARGS[$ARGS_LENGTH]=-f
    ARGS_LENGTH=${ARGS_LENGTH}+1
    ARGS[$ARGS_LENGTH]=mvn:io.uhndata.cards/cards-saml-support/${CARDS_VERSION}/slingosgifeature/base
    ARGS_LENGTH=${ARGS_LENGTH}+1
    ARGS[$ARGS_LENGTH]=-C
    ARGS_LENGTH=${ARGS_LENGTH}+1
    ARGS[$ARGS_LENGTH]=io.dropwizard.metrics:metrics-core:ALL
    ARGS_LENGTH=${ARGS_LENGTH}+1
    ARGS[$ARGS_LENGTH]=-f
    ARGS_LENGTH=${ARGS_LENGTH}+1
    ARGS[$ARGS_LENGTH]=mvn:io.uhndata.cards/cards-fetch-requires-saml-login/${CARDS_VERSION}/slingosgifeature
    ARGS_LENGTH=${ARGS_LENGTH}+1
  elif [[ ${ARGS[$i]} == '--keycloak_demo' ]]
  then
    unset ARGS[$i]
    ARGS[$ARGS_LENGTH]=-f
    ARGS_LENGTH=${ARGS_LENGTH}+1
    ARGS[$ARGS_LENGTH]=mvn:io.uhndata.cards/cards-keycloakdemo-saml-support/${CARDS_VERSION}/slingosgifeature
    ARGS_LENGTH=${ARGS_LENGTH}+1
  elif [[ ${ARGS[$i]} == '--cloud-iam_demo' ]]
  then
    CLOUD_IAM_DEMO="true"
    unset ARGS[$i]
    ARGS[$ARGS_LENGTH]=-f
    ARGS_LENGTH=${ARGS_LENGTH}+1
    ARGS[$ARGS_LENGTH]=mvn:io.uhndata.cards/cards-cloud-iam-demo-saml-support/${CARDS_VERSION}/slingosgifeature
    ARGS_LENGTH=${ARGS_LENGTH}+1
  elif [[ ${ARGS[$i]} == '--uhn_ad_fs' ]]
  then
    unset ARGS[$i]
    ARGS[$ARGS_LENGTH]=-f
    ARGS_LENGTH=${ARGS_LENGTH}+1
    ARGS[$ARGS_LENGTH]=mvn:io.uhndata.cards/cards-uhn-saml-support/${CARDS_VERSION}/slingosgifeature
    ARGS_LENGTH=${ARGS_LENGTH}+1
  elif [[ ${ARGS[$i]} == '--slack_notifications' ]]
  then
    unset ARGS[$i]
    ARGS[$ARGS_LENGTH]=-f
    ARGS_LENGTH=${ARGS_LENGTH}+1
    ARGS[$ARGS_LENGTH]=mvn:io.uhndata.cards/cards-slack-notifications/${CARDS_VERSION}/slingosgifeature
    ARGS_LENGTH=${ARGS_LENGTH}+1
  else
    ARGS[$i]=${ARGS[$i]/VERSION/${CARDS_VERSION}}
  fi
done

SMTPS_ENABLED=false
echo "${ARGS[@]}" | grep -q "mvn:io.uhndata.cards/cards-email-notifications/" && SMTPS_ENABLED=true

if [ $SMTPS_ENABLED = true ]
then
  if [ -z $SLING_COMMONS_CRYPTO_PASSWORD ]
  then
    handle_missing_sling_commons_crypto_warning
    export SLING_COMMONS_CRYPTO_PASSWORD=password
  fi
  if [ ! -e ~/.mailcap ]
  then
    handle_missing_mailcap_fail
  fi
  diff -q ~/.mailcap distribution/mailcap || warn_different_mailcap
fi

if [ $SAML_IN_USE = true ]
then
  if [ $CLOUD_IAM_DEMO = true ]
  then
    HEADERMOD_PROXY_LISTEN_PORT=8080
  else
    HEADERMOD_PROXY_LISTEN_PORT=9090
  fi

  if [ $HEADERMOD_PROXY_LISTEN_PORT = $BIND_PORT ]
  then
    message_saml_proxy_port_conflict_fail
    exit -1
  fi
fi

ERROR_LOG_LAST_MODIFIED_TIME_ORIGIN=$(get_error_log_last_modified)

#Start CARDS in the background
java ${JAVA_DEBUGGING_FLAGS} -Djdk.xml.entityExpansionLimit=0 -Dorg.osgi.service.http.port=${BIND_PORT} -jar distribution/target/dependency/org.apache.sling.feature.launcher.jar -u "file://$(realpath .mvnrepo),file://$(realpath "${HOME}/.m2/repository"),https://nexus.phenotips.org/nexus/content/groups/public,https://repo.maven.apache.org/maven2,https://repository.apache.org/content/groups/snapshots" -p .cards-data -c .cards-data/cache -f mvn:io.uhndata.cards/cards/${CARDS_VERSION}/slingosgifeature/core_${OAK_STORAGE} -f mvn:io.uhndata.cards/cards-dataentry/${CARDS_VERSION}/slingosgifeature/permissions_${PERMISSIONS} "${ARGS[@]}" &
CARDS_PID=$!

if [ ! -z "$JAVA_DEBUGGING_FLAGS" ]
then
  message_connect_jdb
  # As soon as we see CARDS writing to .cards-data/logs/error.log, we
  # can conclude that JDB has attached to the Java process.
  while (( $(echo "$(get_error_log_last_modified) <= $ERROR_LOG_LAST_MODIFIED_TIME_ORIGIN" | bc -l) ))
  do
    sleep 5
    echo "Waiting for JDB attachment..."
  done
fi

#Check to see if CARDS was able to bind to the TCP port
#This is the more robust test that works only if psutil is installed
if [ $PSUTIL_INSTALLED = true ]
then
  for bind_test in `seq 0 $BIND_TESTS`
  do
    #Check if we have timed out
    if [ $bind_test = $BIND_TESTS ]
    then
      kill $CARDS_PID
      wait $CARDS_PID
      handle_tcp_bind_fail
    fi
    sleep $BIND_TEST_SPACING
    #Check if CARDS was able to bind
    python3 Utilities/HostConfig/check_tcp_listen.py --tcp_port $BIND_PORT --pid $CARDS_PID && break
    #If the CARDS Java process has terminated, stop this script altogether
    check_cards_running || handle_cards_java_fail
  done
fi

if [ $PSUTIL_INSTALLED = true ]
then
  handle_tcp_bind_ok_optimal
else
  handle_tcp_bind_ok_suboptimal
fi

#Wait for CARDS to be ready
while true
do
  echo "Waiting for CARDS to start"
  #If the CARDS Java process has terminated, stop this script altogether
  check_cards_running || handle_cards_java_fail
  curl --fail $CARDS_URL/system/sling/info.sessionInfo.json > /dev/null 2> /dev/null && break
  sleep 5
done

#Check if we are in the test runMode
if [ $RUNMODE_TEST = true ]
then
  #If a BIOPORTAL_APIKEY is present, attempt to install HANCESTRO
  if [ ! -z $BIOPORTAL_APIKEY ]
  then
    #Check if HANCESTRO is already installed
    if [ -z $ADMIN_PASSWORD ]
    then
      ADMIN_PASSWORD="admin"
    fi
    curl -u admin:$ADMIN_PASSWORD --fail $CARDS_URL/Vocabularies/HANCESTRO.json > /dev/null 2> /dev/null \
      && HANCESTRO_INSTALLED=true || HANCESTRO_INSTALLED=false
    if [ $HANCESTRO_INSTALLED = false ]
    then
      python3 Utilities/Administration/install_vocabulary.py --bioportal_id HANCESTRO \
        && message_hancestro_install_ok || message_hancestro_install_fail
    else
      echo "HANCESTRO already installed"
    fi
  else
    message_bioportal_apikey_missing
  fi
fi

#Check if we are using the Cloud-IAM.com demo
if [ $CLOUD_IAM_DEMO = true ]
then
  (cd Utilities/Administration/SAML/ && sha256sum -c cloud-iam_demo_samlKeystore.p12.sha256sum && message_sha256_cloud_iam_ok || message_sha256_cloud_iam_error)
  KEYCLOAK_HEADERMOD_HTTP_PROXY_KEYCLOAK_ENDPOINT="https://lemur-15.cloud-iam.com/auth/realms/cards-saml-test/protocol/saml"
fi

#Check if we are using SAML
if [ $SAML_IN_USE = true ]
then
  # Start a keycloak_headermod_http_proxy.js in the background
  if [ -z $KEYCLOAK_HEADERMOD_HTTP_PROXY_KEYCLOAK_ENDPOINT ]
  then
    nodejs Utilities/Development/keycloak_headermod_http_proxy.js \
      --listen-port=$HEADERMOD_PROXY_LISTEN_PORT \
      --cards-port=$BIND_PORT &
    KEYCLOAK_HEADERMOD_HTTP_PROXY_PID=$!
  else
    nodejs Utilities/Development/keycloak_headermod_http_proxy.js \
      --listen-port=$HEADERMOD_PROXY_LISTEN_PORT \
      --cards-port=$BIND_PORT \
      --keycloak-endpoint=$KEYCLOAK_HEADERMOD_HTTP_PROXY_KEYCLOAK_ENDPOINT &
    KEYCLOAK_HEADERMOD_HTTP_PROXY_PID=$!
  fi
fi

message_started_cards
#Stop this script if the CARDS process terminates in failure
wait $CARDS_PID
if [ ! -z $KEYCLOAK_HEADERMOD_HTTP_PROXY_PID ]
then
  kill $KEYCLOAK_HEADERMOD_HTTP_PROXY_PID
  wait $KEYCLOAK_HEADERMOD_HTTP_PROXY_PID
fi
handle_cards_java_fail
