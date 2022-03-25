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

# Check that required environment variables are present
if [ -z $HOST_USER ]
then
  echo "HOST_USER environment variable not specified. Exiting."
  exit -1
fi

if [ -z $HOST_UID ]
then
  echo "HOST_UID environment variable not specified. Exiting."
  exit -1
fi

if [ -z $HOST_GID ]
then
  echo "HOST_GID environment variable not specified. Exiting."
  exit -1
fi

adduser --uid $HOST_UID --disabled-password --gecos "" $HOST_USER

echo "/@.+/ $HOST_USER" > /etc/postfix/virtual
postmap /etc/postfix/virtual

# Create a blank mbox file for the $HOST_USER if no such file exists
if [ ! -e /var/spool/mail/$HOST_USER ]
then
  touch /var/spool/mail/$HOST_USER
  chown $HOST_UID:$HOST_GID /var/spool/mail/$HOST_USER
fi

cp /HOSTPERM_cert.pem /cert.pem
cp /HOSTPERM_certkey.pem /certkey.pem
chown root /cert.pem
chown root /certkey.pem

/usr/sbin/postfix -c /etc/postfix start-fg
