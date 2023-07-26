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

readonly cards_readiness_check_limit = 10

function check_cards_running() {
  docker inspect $(docker-compose ps -q cardsinitial) --format "{{.State.Status}}" | grep "running" > /dev/null
}

function handle_cards_java_fail() {
  echo -e "CARDS has failed to start, cannot run selenium tests"
  exit -1
}

echo "Cleaning up old CARDS Docker Compose deployments"
docker-compose down && docker-compose rm && docker volume prune -f && ./cleanup.sh
echo "Generating CADS Docker Compose environment"
python3 generate_compose_yaml.py --oak_filesystem --dev_docker_image
echo "Launching CARDS Docker Compose environment"
docker-compose build > /dev/null && docker-compose up -d || exit -1

cards_readiness_check=0
while true
do
  echo "Waiting for CARDS to start"
  # If the start_cards process has terminated, halt and fail
  check_cards_running || handle_cards_java_fail
  curl --fail http://localhost:8080/system/sling/info.sessionInfo.json > /dev/null 2> /dev/null && break
  # Fail if cards hasn't started after 10 cycles
  ((cards_readiness_check++)) && ((cards_readiness_check>=cards_readiness_check_limit)) && exit -1
  sleep 5
done
echo "CARDS started"
