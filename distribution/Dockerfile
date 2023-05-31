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

# Start from a small Alpine Linux base image
FROM alpine:3.17

# Install Java JRE and the utilities for patching the JAR file, if such is necessary
RUN apk add \
  openjdk11-jre \
  unzip \
  zip \
  tzdata

# Optional: enable remote debugging at port 5005
ENV DEBUG=

# Optional: enable developer mode
ENV DEV=

# Put everything in /opt/lfs
RUN mkdir -p /opt/lfs/sling/
WORKDIR /opt/lfs/
COPY target/${project.artifactId}-${project.version}.jar .
COPY logback.xml ./sling/

# This is where Sling stores its data
# Make this a volume which can be persisted between different container versions
VOLUME ["/opt/lfs/sling"]

# Expose the 8080 port where normal HTTP connections are expected
EXPOSE 8080

# EXPOSE is just an indication, no port is actually published unless done explicitly when starting the container,
# so it is safe to always declare it here, even when debug mode isn't active
EXPOSE 5005

# This is the default command executed when starting the container
COPY docker_entry.sh /docker_entry.sh
RUN chmod +x /docker_entry.sh
ENTRYPOINT /docker_entry.sh ${project.artifactId} ${project.version}
