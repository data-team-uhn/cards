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

UBUNTU_IMAGE="ubuntu:22.04"
TAG_BACKUP_PATH=~/.docker_tags_backup/nginx_ubuntu_proxy.txt

PROJECT_ROOT=$(realpath ../../)

# Backup the current UBUNTU_IMAGE
./backup_image_tag.sh $UBUNTU_IMAGE $TAG_BACKUP_PATH || { echo "Failed to backup image tag...exiting."; exit -1; }

# Pull the latest UBUNTU_IMAGE
docker pull $UBUNTU_IMAGE || { echo "Failed to pull latest Docker image...exiting."; exit -1; }

# Stop the currently running Ubuntu/Nginx reverse proxy
cd $PROJECT_ROOT
cd compose-cluster
docker-compose stop proxy
docker-compose rm proxy -f
docker volume prune -f

# Build the new Ubuntu/Nginx reverse proxy
docker-compose build proxy || { echo "Failed to build proxy image. You can start the old one with: docker-compose up -d proxy"; exit -1; }

# Start the new Ubuntu/Nginx reverse proxy
docker-compose up -d proxy || { echo "Failed to start proxy"; exit -1; }

# Display an "upgrade finished" message
echo "Finished upgrading Ubuntu/Nginx reverse proxy"
echo "Previous version of $UBUNTU_IMAGE saved to $TAG_BACKUP_PATH"
