#!/bin/bash

export MAVEN_ARTIFACT_GROUP_ID=$1
export MAVEN_ARTIFACT_ARTIFACT_ID=$2
export MAVEN_ARTIFACT_VERSION=$3

envsubst > pom.xml << EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>com.mycompany.app</groupId>
  <artifactId>my-app</artifactId>
  <version>1.0-SNAPSHOT</version>

  <dependencies>
    <dependency>
      <groupId>$MAVEN_ARTIFACT_GROUP_ID</groupId>
      <artifactId>$MAVEN_ARTIFACT_ARTIFACT_ID</artifactId>
      <version>$MAVEN_ARTIFACT_VERSION</version>
    </dependency>
  </dependencies>
</project>
EOF

docker run \
	--rm \
	--network none \
	-v $(realpath ~/trivy-cache):/root/.cache \
	-v $(realpath pom.xml):/my-app/pom.xml \
	aquasec/trivy fs \
	--security-checks vuln \
	--ignore-unfixed \
	/my-app
