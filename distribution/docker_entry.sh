#!/bin/sh

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

#If we are simply restarting...
[ -z $LFS_RELOAD ] || WAIT_FOR_LFSINIT=''
[ -z $LFS_RELOAD ] || INITIAL_SLING_NODE=''

# If we have not explicitly specified the file system as the Oak Repo
# back-end for data storage, use MongoDB
[ -z $OAK_FILESYSTEM ] && OAK_MONGO=true

#If inside a docker-compose environment, wait for a signal...
[ -z $INSIDE_DOCKER_COMPOSE ] || (while true; do (echo "LFS" | nc router 9999) && break; sleep 5; done)

#If (inside a docker-compose environment), we are supposed to wait for http://lfsinitial:8080/ to start
[ -z $WAIT_FOR_LFSINIT ] || (while true; do (wget -S --spider http://lfsinitial:8080/ 2>&1 | grep 'HTTP/1.1 200 OK') && break; sleep 10; done)

PROJECT_ARTIFACTID=$1
PROJECT_VERSION=$2

echo "OAK_MONGO = $OAK_MONGO"
echo "INITIAL_SLING_NODE = $INITIAL_SLING_NODE"
echo "DEV = $DEV"
echo "DEBUG = $DEBUG"
echo "ADDITIONAL_RUN_MODES = $ADDITIONAL_RUN_MODES"
echo "PROJECT_ARTIFACTID = $PROJECT_ARTIFACTID"
echo "PROJECT_VERSION = $PROJECT_VERSION"

[ -z $INITIAL_SLING_NODE ] || SLING_RUN_MODES_CUSTOM=true
[ -z $OAK_MONGO ] || SLING_RUN_MODES_CUSTOM=true
[ -z $DEV ] || SLING_RUN_MODES_CUSTOM=true
[ -z ADDITIONAL_RUN_MODES ] || SLING_RUN_MODES_CUSTOM=true

SLING_RUN_MODES_LIST="${INITIAL_SLING_NODE:+initial_sling_node,}${OAK_MONGO:+oak_mongo,}${DEV:+dev,}${ADDITIONAL_RUN_MODES}"
SLING_RUN_MODES_LIST="${SLING_RUN_MODES_LIST/%,}"

java ${SLING_RUN_MODES_CUSTOM:+-Dsling.run.modes=}$SLING_RUN_MODES_LIST ${DEBUG:+ -Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005} -jar ${PROJECT_ARTIFACTID}-${PROJECT_VERSION}.jar
