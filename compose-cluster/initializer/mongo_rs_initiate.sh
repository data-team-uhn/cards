#!/bin/bash

MONGO_HOST=$1
INIT_DOCUMENT=$2

/usr/bin/mongo --host $MONGO_HOST --port 27017 --eval "rs.initiate($INIT_DOCUMENT);"
