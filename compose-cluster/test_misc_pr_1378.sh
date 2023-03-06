#!/bin/bash

BRANCH_NAME=$1

TERMINAL_NOCOLOR='\033[0m'
TERMINAL_YELLOW='\033[0;33m'

mkdir -p ~/cards-misc-1378-test/

# 000
echo "--- BEGIN TEST 1 ---"
echo -e "${TERMINAL_YELLOW}"
mkdir -p ~/cards-misc-1378-test/001
python3 generate_compose_yaml.py --dev_docker_image --oak_filesystem --composum --cards_project cards4prems
cp docker-compose.yml ~/cards-misc-1378-test/001/${BRANCH_NAME}.yml
./cleanup.sh
echo -e "${TERMINAL_NOCOLOR}"
echo -e "--- END TEST 1 ---"

echo ""
echo ""

# 001
echo "--- BEGIN TEST 2 ---"
echo -e "${TERMINAL_YELLOW}"
mkdir -p ~/cards-misc-1378-test/002
python3 generate_compose_yaml.py --dev_docker_image --oak_filesystem --composum --cards_project cards4prems --server_address cards.localhost:8080
cp docker-compose.yml ~/cards-misc-1378-test/002/${BRANCH_NAME}.yml
./cleanup.sh
echo -e "${TERMINAL_NOCOLOR}"
echo -e "--- END TEST 2 ---"

echo ""
echo ""

# 010
echo "--- BEGIN TEST 3 ---"
echo -e "${TERMINAL_YELLOW}"
mkdir -p ~/cards-misc-1378-test/003
python3 generate_compose_yaml.py --dev_docker_image --oak_filesystem --composum --cards_project cards4prems --smtps
cp docker-compose.yml ~/cards-misc-1378-test/003/${BRANCH_NAME}.yml
./cleanup.sh
echo -e "${TERMINAL_NOCOLOR}"
echo -e "--- END TEST 3 ---"

echo ""
echo ""

# 011
echo "--- BEGIN TEST 4 ---"
echo -e "${TERMINAL_YELLOW}"
mkdir -p ~/cards-misc-1378-test/004
python3 generate_compose_yaml.py --dev_docker_image --oak_filesystem --composum --cards_project cards4prems --server_address cards.localhost:8080 --smtps
cp docker-compose.yml ~/cards-misc-1378-test/004/${BRANCH_NAME}.yml
./cleanup.sh
echo "--- END TEST 4 ---"
echo -e "${TERMINAL_NOCOLOR}"

echo ""
echo ""

# 100
echo "--- BEGIN TEST 5 ---"
echo -e "${TERMINAL_YELLOW}"
mkdir -p ~/cards-misc-1378-test/005
python3 generate_compose_yaml.py --dev_docker_image --oak_filesystem --composum --cards_project cards4prems --saml
cp docker-compose.yml ~/cards-misc-1378-test/005/${BRANCH_NAME}.yml
./cleanup.sh
echo "--- END TEST 5 ---"
echo -e "${TERMINAL_NOCOLOR}"

echo ""
echo ""

# 101
echo "--- BEGIN TEST 6 ---"
echo -e "${TERMINAL_YELLOW}"
mkdir -p ~/cards-misc-1378-test/006
python3 generate_compose_yaml.py --dev_docker_image --oak_filesystem --composum --cards_project cards4prems --saml --server_address cards.localhost:8080
cp docker-compose.yml ~/cards-misc-1378-test/006/${BRANCH_NAME}.yml
./cleanup.sh
echo "--- END TEST 6 ---"
echo -e "${TERMINAL_NOCOLOR}"

echo ""
echo ""

# 110
echo "--- BEGIN TEST 7 ---"
echo -e "${TERMINAL_YELLOW}"
mkdir -p ~/cards-misc-1378-test/007
python3 generate_compose_yaml.py --dev_docker_image --oak_filesystem --composum --cards_project cards4prems --saml --smtps
cp docker-compose.yml ~/cards-misc-1378-test/007/${BRANCH_NAME}.yml
./cleanup.sh
echo "--- END TEST 7 ---"
echo -e "${TERMINAL_NOCOLOR}"

echo ""
echo ""

# 111
echo "--- BEGIN TEST 8 ---"
echo -e "${TERMINAL_YELLOW}"
mkdir -p ~/cards-misc-1378-test/008
python3 generate_compose_yaml.py --dev_docker_image --oak_filesystem --composum --cards_project cards4prems --saml --smtps --server_address cards.localhost:8080
cp docker-compose.yml ~/cards-misc-1378-test/008/${BRANCH_NAME}.yml
./cleanup.sh
echo "--- END TEST 8 ---"
echo -e "${TERMINAL_NOCOLOR}"
