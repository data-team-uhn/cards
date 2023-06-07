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

#Original Dockerfile entrypoint
(mongos --config /etc/mongo.conf) &
MONGOS_PID=$!

#Wait for it to be ready...
until /usr/bin/mongosh --quiet --eval 'db.getMongo()'; do
	sleep 1
done

/usr/bin/mongosh <<EOF
  use sling;
  
  db.createCollection("blobs");
  db.createCollection("clusterNodes");
  db.createCollection("journal");
  db.createCollection("nodes");
  db.createCollection("settings");
  
  db.blobs.ensureIndex({ _id : "hashed" });
  sh.enableSharding("sling");
  sh.shardCollection("sling.blobs", { _id : "hashed" });
  
  db.clusterNodes.ensureIndex({ _id : "hashed" });
  sh.enableSharding("sling");
  sh.shardCollection("sling.clusterNodes", { _id : "hashed" });
  
  db.journal.ensureIndex({ _id : "hashed" });
  sh.enableSharding("sling");
  sh.shardCollection("sling.journal", { _id : "hashed" });
  
  db.nodes.ensureIndex({ _id : "hashed" });
  sh.enableSharding("sling");
  sh.shardCollection("sling.nodes", { _id : "hashed" });
  
  db.settings.ensureIndex({ _id : "hashed" });
  sh.enableSharding("sling");
  sh.shardCollection("sling.settings", { _id : "hashed" });
  
  use config;
  db.settings.save( {_id : "chunksize", value: 1 } );
EOF

#CARDS (Apache Sling) is now allowed to start
nc -l -p 9999 -q 0

#Prevent script from exiting
wait $MONGOS_PID
