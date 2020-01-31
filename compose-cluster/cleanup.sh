#!/bin/bash

echo "Removing shard directories"
rm -r shard*

echo "Removing docker-compose.yml"
rm docker-compose.yml

echo "Removing initializer/initialize_all.sh"
rm initializer/initialize_all.sh

echo "Done"
