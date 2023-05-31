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

# Use Docker multi-stage building so that the production image may be
# slim yet still be self-contained with all the necessary dependency
# JARs

# Build Image
FROM alpine:3.17

ARG build_jars

# Copy in the make_filled_m2.sh script and make it executable
COPY make_filled_m2.sh /
RUN chmod +x /make_filled_m2.sh

# Copy in the source code
ADD COMPLETE_SOURCE_CODE.tar /cards

# Build so that we can gather all dependency JARs, if docker.buildArg.build_jars=true
WORKDIR /cards
RUN if [[ "$build_jars" == "true" ]] ; then /make_filled_m2.sh ; else mkdir /root/.m2 ; fi

# Create a directory for storing basic metadata about the built image
RUN mkdir /metadata

# Copy in the yarn.lock file as metadata
RUN if [[ "$build_jars" == "true" ]] ; then cp /cards/aggregated-frontend/src/main/frontend/yarn.lock /metadata/yarn.lock ; fi

# Production Image: Start from a small Alpine Linux base image
FROM alpine:3.17

# Install dependency Alpine Linux packages and apply any available package upgrades
RUN apk update
RUN apk add --upgrade apk-tools
RUN apk upgrade --available
RUN apk add \
  openjdk11-jre \
  tzdata

# Optional: enable remote debugging at port 5005
ENV DEBUG=

# Optional: enable developer mode
ENV DEV=

# Put everything in /opt/cards
RUN mkdir -p /opt/cards/.cards-data/
WORKDIR /opt/cards/
COPY target/dependency/org.apache.sling.feature.launcher.jar .
COPY target/${project.artifactId}-${project.version}-core_mongo_far.far .
COPY target/${project.artifactId}-${project.version}-core_tar_far.far .
COPY logback.xml ./.cards-data/
COPY mailcap /root/.mailcap

# This is where Sling stores its data
# Make this a volume which can be persisted between different container versions
VOLUME ["/opt/cards/.cards-data"]

# Expose the 8080 port where normal HTTP connections are expected
EXPOSE 8080

# EXPOSE is just an indication, no port is actually published unless done explicitly when starting the container,
# so it is safe to always declare it here, even when debug mode isn't active
EXPOSE 5005

# Copy in the dependency JARs
COPY --from=0 /root/.m2 /root/.m2

# Copy in the build metadata
COPY --from=0 /metadata /metadata

# This is the default command executed when starting the container
COPY docker_entry.sh /docker_entry.sh
RUN chmod +x /docker_entry.sh
ENTRYPOINT /docker_entry.sh ${project.artifactId} ${project.version}
