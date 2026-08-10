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

# The same image definition serves two flavors, differing only in how much of the artifact
# repository is baked in by the Maven build preparing the build context:
# - the developer flavor (default) only embeds the project artifacts (mvnrepo/); third-party
#   artifacts come from a volume-mounted ~/.m2, or are downloaded on first start
# - the production flavor (-Pproduction) also embeds every third-party artifact
#   referenced by any feature in the repository (artifacts/), needing no network access

# Start from a small Alpine Linux base image
FROM alpine:3.24

# Install dependency Alpine Linux packages and apply any available package upgrades
RUN apk update
RUN apk add --upgrade apk-tools
RUN apk upgrade --available
RUN apk add \
  openjdk21-jre \
  python3 \
  tzdata \
  bash

# Optional: enable remote debugging at port 5005
ENV DEBUG=

# Optional: enable developer mode
ENV DEV=

# Put everything in /opt/cards
RUN mkdir -p /opt/cards/.cards-data/
WORKDIR /opt/cards/

# The feature launcher
COPY target/dependency/org.apache.sling.feature.launcher/ org.apache.sling.feature.launcher/

# The artifact repositories: the project artifacts, including all the feature files, and the
# (possibly empty) third-party artifact repository
COPY target/context/mvnrepo/ mvnrepo/
COPY target/context/artifacts/ artifacts/

# The build metadata, supporting security audits of deployments
COPY target/context/metadata/ /metadata/

COPY logback.xml ./.cards-data/
COPY mailcap /root/.mailcap
COPY get_project_dependency_features.py /get_project_dependency_features.py
COPY sling-features.json /sling-features.json

# The mount point for a host ~/.m2 in the developer flavor; harmless when not mounted
RUN mkdir -p /root/.m2/repository

# An extra artifact repository that deployment tooling may inject artifacts into
RUN mkdir -p /root/.cards-generic-m2/repository

# This is where Sling stores its data
# Make this a volume which can be persisted between different container versions
VOLUME ["/opt/cards/.cards-data"]

# Expose the 8080 port where normal HTTP connections are expected
EXPOSE 8080

# EXPOSE is just an indication, no port is actually published unless done explicitly when starting the container,
# so it is safe to always declare it here, even when debug mode isn't active
EXPOSE 5005

# This is the default command executed when starting the container
COPY docker_entry.sh /docker_entry.sh
RUN chmod +x /docker_entry.sh
ENTRYPOINT /docker_entry.sh cards ${project.version}
