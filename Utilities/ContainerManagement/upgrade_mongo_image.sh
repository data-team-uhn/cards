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

MONGO_CONTAINER_INSTANCE=$1
MONGO_IMAGE="mongo:6.0-jammy"
TAG_BACKUP_PATH=~/.docker_tags_backup/singular_mongo_slingstore.txt

# Check if jq is installed. Exit if it is not.
echo '' | jq . >/dev/null 2>/dev/null || { echo "jq is not installed...exiting."; exit -1; }

PROJECT_ROOT=$(realpath ../../)

# Check that MONGO_CONTAINER_INSTANCE is a real, running container
docker top $MONGO_CONTAINER_INSTANCE 2>/dev/null >/dev/null || { echo "Invalid specified container...exiting."; exit -1; }

# Check that the container has bind-mounted a local directory to /data/db
DATA_DIRECTORY_MOUNT=$(docker inspect -f '{{json .HostConfig.Binds }}' $MONGO_CONTAINER_INSTANCE | jq -r '.[] | select(test("^.+:/data/db(:rw){0,1}$"))' | cut -d  ':' -f1)
[ -z $DATA_DIRECTORY_MOUNT ] && { echo "/data/db is not bind-mounted...exiting."; exit -1; }

# Backup the current MONGO_IMAGE
./backup_image_tag.sh $MONGO_IMAGE $TAG_BACKUP_PATH || { echo "Failed to backup image tag...exiting."; exit -1; }

# Pull the latest MONGO_IMAGE
docker pull $MONGO_IMAGE || { echo "Failed to pull latest Docker image...exiting."; exit -1; }

# Stop the Docker Compose environment
cd $PROJECT_ROOT
cd compose-cluster
docker-compose down
docker-compose rm -f
docker volume prune -f

# Stop the specified running MongoDB
docker stop $MONGO_CONTAINER_INSTANCE
docker rm $MONGO_CONTAINER_INSTANCE

# Start the specified MongoDB (using the new image) with the same volume mount as before
docker run -p 27017:27017 -v $DATA_DIRECTORY_MOUNT:/data/db --name $MONGO_CONTAINER_INSTANCE -d $MONGO_IMAGE || { echo "Failed to start MongoDB container...exiting."; exit -1; }

# Bring everything back up
docker-compose up -d
