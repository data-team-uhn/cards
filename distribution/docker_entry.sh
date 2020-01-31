#!/bin/sh

#If inside a docker-compose environment, wait for a signal...
[ -z $INSIDE_DOCKER_COMPOSE ] || (while true; do (echo "LFS" | nc router 9999) && break; sleep 5; done)

#If (inside a docker-compose environment), we are supposed to wait for http://lfsinitial:8080/ to start
[ -z $WAIT_FOR_LFSINIT ] || (while true; do (wget -S --spider http://lfsinitial:8080/ 2>&1 | grep 'HTTP/1.1 200 OK') && break; sleep 10; done)

PROJECT_ARTIFACTID=$1
PROJECT_VERSION=$2

echo "INITIAL_SLING_NODE = $INITIAL_SLING_NODE"
echo "DEV = $DEV"
echo "DEBUG = $DEBUG"
echo "PROJECT_ARTIFACTID = $PROJECT_ARTIFACTID"
echo "PROJECT_VERSION = $PROJECT_VERSION"

java -Dsling.run.modes=${INITIAL_SLING_NODE:+initial_sling_node,}oak_mongo${DEV:+,dev} ${DEBUG:+ -Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005} -jar ${PROJECT_ARTIFACTID}-${PROJECT_VERSION}.jar
