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
[ -z $CARDS_RELOAD ] || WAIT_FOR_CARDSINIT=''

# If we have not explicitly specified the file system as the Oak Repo
# back-end for data storage, use MongoDB
STORAGE=tar
[ -z $OAK_FILESYSTEM ] && STORAGE=mongo

#If inside a docker-compose environment, wait for a signal...
[ -z $INSIDE_DOCKER_COMPOSE ] || (while true; do (echo "CARDS" | nc router 9999) && break; sleep 5; done)

#If (inside a docker-compose environment), we are supposed to wait for http://cardsinitial:8080/ to start
[ -z $WAIT_FOR_CARDSINIT ] || (while true; do (wget -S --spider http://cardsinitial:8080/ 2>&1 | grep 'HTTP/1.1 200 OK') && break; sleep 10; done)

PROJECT_ARTIFACTID=$1
PROJECT_VERSION=$2

echo "STORAGE = $STORAGE"
echo "DEV = $DEV"
echo "DEBUG = $DEBUG"
echo "PROJECT_ARTIFACTID = $PROJECT_ARTIFACTID"
echo "PROJECT_VERSION = $PROJECT_VERSION"

#Execute the volume_mounted_init.sh script if it is present
[ -e /volume_mounted_init.sh ] && /volume_mounted_init.sh

java -Djdk.xml.entityExpansionLimit=0 ${DEBUG:+ -Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005} -jar org.apache.sling.feature.launcher.jar -f ./${PROJECT_ARTIFACTID}-${PROJECT_VERSION}-core_${STORAGE}_far.far
