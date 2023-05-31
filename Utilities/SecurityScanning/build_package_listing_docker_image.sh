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

INPUT_DOCKER_IMAGE=$1
OUTPUT_DOCKER_IMAGE=$2

docker build --no-cache -t $OUTPUT_DOCKER_IMAGE - << EOF
FROM scratch
COPY --from=$INPUT_DOCKER_IMAGE /etc/os-release /etc/os-release
COPY --from=$INPUT_DOCKER_IMAGE /etc/apk /etc/apk
COPY --from=$INPUT_DOCKER_IMAGE /lib/apk/db /lib/apk/db
EOF
