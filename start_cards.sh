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

function handle_tcp_bind_fail() {
  echo -e "${TERMINAL_RED}**********************************${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}*                                *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}*   Unable to bind to TCP port   *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}*                                *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_RED}**********************************${TERMINAL_NOCOLOR}"
  exit -1
}

function handle_tcp_bind_ok_optimal() {
  echo -e "${TERMINAL_GREEN}*********************${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_GREEN}*                   *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_GREEN}*   Started CARDS   *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_GREEN}*                   *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_GREEN}*********************${TERMINAL_NOCOLOR}"
}

function handle_tcp_bind_ok_suboptimal() {
  echo -e "${TERMINAL_YELLOW}*************************************************${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_YELLOW}*                                               *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_YELLOW}*   Started CARDS - used suboptimal bind test   *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_YELLOW}*                                               *${TERMINAL_NOCOLOR}"
  echo -e "${TERMINAL_YELLOW}*************************************************${TERMINAL_NOCOLOR}"
}

#Determine the port that CARDS is to bind to
BIND_PORT=$(ARGUMENT_KEY='-p' ARGUMENT_DEFAULT='8080' python3 Utilities/HostConfig/argparse_bash.py $@)

#Check if the psutil Python module is installed
python3 -c 'import psutil' 2>/dev/null && PSUTIL_INSTALLED=true || PSUTIL_INSTALLED=false

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
  done
fi

if [ $PSUTIL_INSTALLED = true ]
then
  handle_tcp_bind_ok_optimal
else
  handle_tcp_bind_ok_suboptimal
fi

#Wait for CTRL+C to stop everything
read -r -d '' _ < /dev/tty
