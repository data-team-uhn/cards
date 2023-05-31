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

OUTPUT_DOCKER_IMAGE=$1

if [ -z $OUTPUT_DOCKER_IMAGE ]
then
	echo "You must specify an output Docker image name (eg. ./build_self_contained.sh cards/cards:latest-dev)"
	exit 1
fi

# First check that the cards/sling-feature-downloader Docker image exists on the local machine
(docker image inspect cards/sling-feature-downloader > /dev/null) || {
	echo "Fail: The cards/sling-feature-downloader Docker image does not exist.";
	echo "It can be obtained from https://github.com/data-team-uhn/cards-sling-feature-downloader."
	echo "Exiting."
	exit 1;
}

UTILITIES_PACKAGING_DOCKER_DIRECTORY=$(pwd)
export CARDS_DIRECTORY=$(realpath ../../../)

# Switch to the root of the CARDS repository
cd $CARDS_DIRECTORY

# Get the version of CARDS that we will build
CARDS_VERSION=$(cat ${CARDS_DIRECTORY}/pom.xml | grep --max-count=1 '<version>' | cut '-d>' -f2 | cut '-d<' -f1)

# If this is a release, build in production mode, otherwise build in dev mode
# If building in production mode, we want to ensure that the Docker cache is
# disabled so that we are using the latest Alpine Linux packages
MAVEN_BUILD_ARGS=""
(echo "$CARDS_VERSION" | grep -e '-SNAPSHOT$' -q) || MAVEN_BUILD_ARGS="-Prelease -Dgpg.skip -Dmaven.javadoc.skip -Ddocker.buildOptions.noCache=true"

# Build CARDS including a Docker image
mvn clean install -Pdocker $MAVEN_BUILD_ARGS || { echo "Failed to build CARDS Docker image. Exiting."; exit -1; }

# Get the name of the newly built Docker image
INPUT_DOCKER_IMAGE="cards/cards:${CARDS_VERSION}"
(echo "$CARDS_VERSION" | grep -e '-SNAPSHOT$' -q) && INPUT_DOCKER_IMAGE="cards/cards:latest"

# Switch back to the Utilities/Packaging/Docker directory
cd $UTILITIES_PACKAGING_DOCKER_DIRECTORY

# --- Based on run.sh from cards-sling-feature-downloader utility

export DEPLOYMENT_M2_DIRECTORY=$(realpath $(mktemp -d -p .))

IFS=$'\n'
for pkg in $(cat cards_feature_list.txt | grep -v -e '^#' | grep -e '^.' | PROJECT_VERSION=$CARDS_VERSION envsubst)
do
	echo "Getting $pkg"
	./download_feature_dependencies.sh "$pkg" "tar" || { echo "FAILED!"; exit 1; }
	./download_feature_dependencies.sh "$pkg" "mongo" || { echo "FAILED!"; exit 1; }
done

./add_artifacts_to_docker_image.sh $INPUT_DOCKER_IMAGE $OUTPUT_DOCKER_IMAGE $DEPLOYMENT_M2_DIRECTORY || { echo "FAILED!"; exit 1; }
rm -rf $DEPLOYMENT_M2_DIRECTORY || { echo "FAILED!"; exit 1; }
