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

resolve_ip () {
	HOSTNAME=$1 python3 -c 'import os; import socket; print(os.environ["HOSTNAME"] + ":" + socket.gethostbyname(os.environ["HOSTNAME"]))'
}

docker run \
	--rm \
	--add-host $(resolve_ip ghcr.io) \
	--add-host $(resolve_ip index.docker.io) \
	--add-host $(resolve_ip production.cloudflare.docker.com) \
	-v $(realpath ~/trivy-cache):/root/.cache \
	aquasec/trivy fs \
	--download-db-only
