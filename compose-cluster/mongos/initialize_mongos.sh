#!/bin/bash

#Original Dockerfile entrypoint
(mongos --config /etc/mongo.conf) &
MONGOS_PID=$!

#Wait for it to be ready...
until /usr/bin/mongo --quiet --eval 'db.getMongo()'; do
	sleep 1
done

/usr/bin/mongo <<EOF
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

#LFS (Apache Sling) is now allowed to start
nc -l -p 9999 -q 0

#Prevent script from exiting
wait $MONGOS_PID
