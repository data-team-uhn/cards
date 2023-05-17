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
JARS_M2_DIRECTORY=$3

touch docker_context.tar || exit -1
DOCKER_CONTEXT_TAR_PATH=$(realpath docker_context.tar) || exit -1

# Add all the JAR files into the Docker context
(cd $JARS_M2_DIRECTORY && tar -c -v -f $DOCKER_CONTEXT_TAR_PATH .) || exit -1

# Create a Docker context
mkdir .tmpctx || exit -1
cd .tmpctx

# Add metadata files to the Docker context
mkdir metadata

# Add the yarn.lock file listing the contained JavaScript packages to the metadata
cp $CARDS_DIRECTORY/aggregated-frontend/src/main/frontend/yarn.lock metadata/yarn.lock

# Add a Dockerfile to the Docker context
cat << EOF > Dockerfile || exit -1
FROM $INPUT_DOCKER_IMAGE
COPY repository /root/.m2/repository
COPY metadata /metadata
EOF

# Build the Docker context into a usable Docker image
tar -r -v -f $DOCKER_CONTEXT_TAR_PATH . || exit -1
cd ..
rm -rf .tmpctx || exit -1

cat $DOCKER_CONTEXT_TAR_PATH | docker build -t $OUTPUT_DOCKER_IMAGE - || exit -1
rm $DOCKER_CONTEXT_TAR_PATH || exit -1
