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

# Start from a small JRE8 base image
FROM openjdk:8-jre-alpine

# Put everything in /opt/lfs
RUN mkdir -p /opt/lfs/
WORKDIR /opt/lfs/
COPY target/${project.artifactId}-${project.version}.jar .

# This is where Sling stores its data
# Make this a volume which can be persisted between different container versions
VOLUME ["/opt/lfs/sling"]

# Expose the 8080 port where normal HTTP connections are expected
EXPOSE 8080

# Optional: enable remote debugging at port 5005
ENV DEBUG=
# EXPOSE is just an indication, no port is actually published unless done explicitly when starting the container,
# so it is safe to always declare it here, even when debug mode isn't active
EXPOSE 5005

# Optional: enable developer mode
ENV DEV=

# This is the default command executed when starting the container
ENTRYPOINT java ${DEBUG:+ -Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005} -jar ${project.artifactId}-${project.version}.jar ${DEV:+-Dsling.run.modes=dev}
