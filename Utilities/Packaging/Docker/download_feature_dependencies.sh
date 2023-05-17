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

MAVEN_FEATURE_NAME=$1
STORAGE_TYPE=$2

docker run \
	--rm \
	--user $UID:$(id -g) \
	-v ~/.m2:/host_m2:ro \
	-v $CARDS_DIRECTORY:/cards:ro \
	-v $DEPLOYMENT_M2_DIRECTORY:/m2 \
	-e MAVEN_FEATURE_NAME="$MAVEN_FEATURE_NAME" \
	-e STORAGE_TYPE="$STORAGE_TYPE" \
	--network none \
	-it cards/sling-feature-downloader
