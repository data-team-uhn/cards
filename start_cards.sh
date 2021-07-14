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
  echo "Shutting down CARDS"
  kill $CARDS_PID
  wait $CARDS_PID
  exit
}

function check_cards_running() {
  jobs -pr | grep '^'$CARDS_PID'$' > /dev/null
}

function handle_cards_java_fail() {
  echo -e "${TERMINAL_RED}*****************************************${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}*                                       *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}*   The CARDS Java process has failed   *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}*                                       *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}*****************************************${TERMINAL_NOCOLOR}"
  exit -1
}

function handle_tcp_bind_fail() {
  echo -e "${TERMINAL_RED}**********************************${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}*                                *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}*   Unable to bind to TCP port   *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}*                                *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}**********************************${TERMINAL_NOCOLOR}"
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

function message_started_cards() {
  echo -e "${TERMINAL_GREEN}*********************${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_GREEN}*                   *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_GREEN}*   Started CARDS   *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_GREEN}*                   *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_GREEN}*********************${TERMINAL_NOCOLOR}"
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

#Start CARDS in the background
java -jar distribution/target/lfs-*jar $@ &
CARDS_PID=$!

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

message_started_cards

#Stop this script if the CARDS process terminates in failure
wait $CARDS_PID
handle_cards_java_fail
