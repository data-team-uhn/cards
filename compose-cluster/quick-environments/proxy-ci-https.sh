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

# HTTPS
python3 generate_compose_yaml.py \
  --dev_docker_image \
  --composum \
  --oak_filesystem \
  --server_address localhost \
  --web_port_admin 443 \
  --web_port_user 444 \
  --cards_project cards4prems \
  --ssl_proxy \
  --self_signed_ssl_proxy

docker-compose build
docker-compose up -d

while true
do
  curl --fail --insecure https://localhost/system/sling/info.sessionInfo.json && break
  sleep 5
done
