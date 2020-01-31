#!/bin/bash

MONGO_HOST=$1

until /usr/bin/mongo --host $MONGO_HOST --port 27017 --quiet --eval 'db.getMongo()'; do
	sleep 1
done
