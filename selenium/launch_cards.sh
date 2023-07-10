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

CARDS_PID=$(pgrep "start_cards.sh")

function check_cards_running() {
  docker ps | grep compose-cluster-cardsinitial-1 > /dev/null
}

function handle_cards_java_fail() {
  echo -e "Cards has failed to start, cannot run selenium tests"
  exit -1
}

echo "Cleaning up old CARDS docker images"
docker-compose down && docker-compose rm && docker volume prune -f && ./cleanup.sh
echo "Generating docker configuration"
python3 generate_compose_yaml.py --oak_filesystem --dev_docker_image
echo "Launching CARDS docker image"
docker-compose build > /dev/null && docker-compose up -d

while true
do
  echo "Waiting for CARDS to start"
  # If the start_cards process has terminated, halt and fail
  check_cards_running || handle_cards_java_fail
  curl --fail http://localhost:8080/system/sling/info.sessionInfo.json > /dev/null 2> /dev/null && break
  # Fail if cards hasn't started after 10 cycles
  ((c++)) && ((c==10)) && exit -1
  sleep 5
done
echo "CARDS started"
