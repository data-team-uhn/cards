#!/bin/bash

MONGO_HOST=$1
SHARD_CONFIG=$2

/usr/bin/mongo --host $MONGO_HOST --port 27017 --eval "sh.addShard(\"$SHARD_CONFIG\");"
