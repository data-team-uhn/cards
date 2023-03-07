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

SSL_CERT=$1
SSL_KEY=$2
MBOX_DIR=$3

docker run --rm \
  -e HOST_USER=$USER \
  -e HOST_UID=$UID \
  -e HOST_GID=$(id -g) \
  -p 127.0.0.1:8025:25 \
  -p 127.0.0.1:8465:465 \
  -v $(realpath $MBOX_DIR):/var/spool/mail \
  -v $(realpath $SSL_CERT):/HOSTPERM_cert.pem:ro \
  -v $(realpath $SSL_KEY):/HOSTPERM_certkey.pem:ro \
  -it cards/postfix-docker
