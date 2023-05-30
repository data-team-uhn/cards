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

SECURITY_SCANNING_DIRECTORY=$(pwd)
DOCKER_PACKAGING_DIRECTORY=$(realpath ../Packaging/Docker)
SCAN_START_DATE=$(date +'%Y-%m-%d_%H-%M-%S')
OUTPUT_REPORT_FILENAME="${HOME}/cards_security_scan_${SCAN_START_DATE}.md"

# Build a self-contained CARDS Docker image
cd $DOCKER_PACKAGING_DIRECTORY
./build_self_contained.sh cards/cards-self-contained:latest || { echo "Failed to build a self-contained CARDS Docker image. Exiting."; exit -1; }

cd $SECURITY_SCANNING_DIRECTORY

# Get maven.json
./list_maven_packages_in_docker_image.sh cards/cards-self-contained:latest maven.json || { echo "Failed to get maven.json. Exiting."; exit -1; }

# Get yarn.lock
./get_yarn_lock_file_for_docker_image.sh cards/cards-self-contained:latest yarn.lock || { echo "Failed to get yarn.lock. Exiting."; exit -1; }

# Pull the latest aquasec/trivy Docker image
docker pull aquasec/trivy:latest || { echo "Failed to pull aquasec/trivy:latest. Exiting."; exit -1; }

# Update the Trivy Cache
cd trivy-utils
./update_trivy_cache.sh || { echo "Failed to run update_trivy_cache.sh. Exiting."; exit -1; }

# Scan maven.json with Trivy
python3 scan_maven_package_list.py --maven_package_list ../maven.json --markdown_report_file ../maven_issues.md || { echo "Failed to scan Maven packages with Trivy. Exiting."; exit -1; }

# Scan yarn.lock with Trivy
docker run \
	--rm \
	--network none \
	-v $(realpath ~/trivy-cache):/root/.cache \
	-v $(realpath ../yarn.lock):/yarn.lock:ro \
	aquasec/trivy fs \
	--security-checks vuln \
	--ignore-unfixed /yarn.lock \
	--format json | python3 trivy_to_slack.py --package_emoji :octocat: --markdown_report_file ../npm_issues.md || { echo "Failed to scan NPM/Yarn packages with Trivy. Exiting."; exit -1; }

cd ..

# Build the complete report
echo "# CARDS Security scan of branch $(git symbolic-ref --short HEAD)" > $OUTPUT_REPORT_FILENAME
echo "## On ${SCAN_START_DATE}" >> $OUTPUT_REPORT_FILENAME
echo "" >> $OUTPUT_REPORT_FILENAME
echo "" >> $OUTPUT_REPORT_FILENAME

echo "### Maven Packages" >> $OUTPUT_REPORT_FILENAME
echo "" >> $OUTPUT_REPORT_FILENAME

cat maven_issues.md >> $OUTPUT_REPORT_FILENAME
echo "" >> $OUTPUT_REPORT_FILENAME
echo "" >> $OUTPUT_REPORT_FILENAME

echo "### NPM/Yarn Packages" >> $OUTPUT_REPORT_FILENAME
echo "" >> $OUTPUT_REPORT_FILENAME

cat npm_issues.md >> $OUTPUT_REPORT_FILENAME
echo "" >> $OUTPUT_REPORT_FILENAME
echo "" >> $OUTPUT_REPORT_FILENAME

# Clean up
rm maven.json
rm yarn.lock
rm maven_issues.md
rm npm_issues.md

# Remove the self-contained CARDS Docker image
docker rmi cards/cards-self-contained:latest

# Success
echo "Finished - security report saved to $OUTPUT_REPORT_FILENAME"
