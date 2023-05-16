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

echo "Removing shard directories"
rm -r shard*

echo "Removing docker-compose.yml"
rm docker-compose.yml

echo "Removing initializer/initialize_all.sh"
rm initializer/initialize_all.sh

echo "Removing mongos/mongo-router.conf"
rm mongos/mongo-router.conf

echo "Removing proxy/000-default.conf"
rm proxy/000-default.conf

echo "Removing proxy/proxyerror/logo.png"
rm proxy/proxyerror/logo.png

echo "Removing smtps_localhost_proxy/nginx.conf"
rm smtps_localhost_proxy/nginx.conf

echo "Removing secrets"
rm -r secrets

# Due permissions issues, we have to first remove all contents of SLING with Docker
echo "Removing SLING"
docker run --rm -v $(realpath ./SLING):/sling -it alpine:3.17 sh -c 'cd /sling; find . -delete'
rm -rf SLING

echo "Done"
