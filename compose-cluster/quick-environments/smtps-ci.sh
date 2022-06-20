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

if [[ "$#" -ne 1 ]]
then
  echo "Proper usage: smtps-ci.sh /path/to/mail/directory"
  exit 1
fi

MAILDIR_PATH=$1

if [[ ! -d $MAILDIR_PATH ]]
then
  echo "The directory $MAILDIR_PATH does not exist. Exiting."
  exit 1
fi

python3 generate_compose_yaml.py \
  --cards_project cards4proms \
  --dev_docker_image \
  --composum \
  --oak_filesystem \
  --server_address localhost:8080 \
  --smtps \
  --smtps_test_container \
  --smtps_test_mail_path $MAILDIR_PATH

docker-compose build
docker-compose up -d

while true
do
  curl --fail http://localhost:8080/system/sling/info.sessionInfo.json && break
  sleep 5
done
