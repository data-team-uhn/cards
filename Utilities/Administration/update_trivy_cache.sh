#!/bin/bash

docker run \
	--rm \
	-v $(realpath ~/trivy-cache):/root/.cache \
	aquasec/trivy fs \
	--download-db-only
