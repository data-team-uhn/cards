# CARDS (Clinical Archive for Data Science)
###### Based on Apache Sling

[![Build Status](https://travis-ci.com/ccmbioinfo/lfs.svg?branch=dev)](https://travis-ci.com/ccmbioinfo/lfs)

## Prerequisites:
* Java 11
* Maven 3.3+
* Python 2.5+ or Python 3.0+
* psutil Python module (recommended)

## Build:
`mvn clean install`

#### To build a self-contained Docker image:
`MAVEN_OPTS="-Ddocker.verbose -Ddocker.buildArg.build_jars=true" mvn clean install`

Additional options include:

`mvn clean -Pclean-node` to remove compiled frontend code

`mvn clean -Pclean-instance` to remove the sling/ folder generated by running Sling

`mvn install -Pquick` to skip many of the test steps

`mvn install -Pskip-webpack` to skip reinstalling webpack/yarn and dependencies and skip regenerating the frontend with webpack

`mvn install -PautoInstallBundle` to inject compiled code into a running instance of Sling at `http://localhost:8080/` with admin:admin.

To specify a different password, use `-Dsling.password=newPassword`

To specify a different URL, use `-Dsling.url=https://cards.server:8443/system/console` (the URL must end with `/system/console` to work properly)

`mvn install -PintegrationTests` to run integration tests

## Run:
`./start_cards.sh` => the app will run at `http://localhost:8080` (default port)

`./start_cards.sh -p PORT` to run at a different port

`./start_cards.sh -P PROJECT1,PROJECT2` to run a specific project. Currently supported projects are: `cards4lfs`, `cards4kids`, `cards4care`, `cards4proms`.

`./start_cards.sh --permissions SCHEME` to run with a different permission scheme. Currently supported schemes are:
- `open`, the default, where all registered users can create, view and edit all records
- `trusted`, where only users explicitly added to the `TrustedUsers` group can access records
- `ownership`, where all users can create new records, but only the creator of a record can view and edit it

`./start_cards.sh --dev` to include the content browser (Composum), accessible at `http://localhost:8080/bin/browser.html`

`./start_cards.sh --test` to include the test questionnaires

`./start_cards.sh --demo` to include the demo warning banner

By default, the app will run with username `admin` and password `admin`.

In order to use "Vocabularies" section and load vocabularies from BioPortal (bioontology.org) `BIOPORTAL_APIKEY` environment variable should be set to a valid BioPortal API key. You can [request a new account](https://bioportal.bioontology.org/accounts/new) if you don't already have one, and the API key can be found [in your profile](https://bioportal.bioontology.org/account).

## Running with Docker

If Docker is installed, then the build will also create a new image named `cards/cards:latest`

### Test/Development Environments

CARDS can be ran as a *single* Docker container using the file system (instead of Mongo)
as a data storage back-end for Apache Sling.

```bash
docker run --rm -e INITIAL_SLING_NODE=true -e OAK_FILESYSTEM=true -p 127.0.0.1:8080:8080 -it cards/cards
```

### Production Environments

Before the Docker container can be started, an isolated network providing MongoDB must be established. To do so:

```bash
docker network create cardsbridge
docker run --rm --network cardsbridge --name mongo -d mongo
```

For basic testing of the CARDS Docker image, run:

```bash
docker run --rm --network cardsbridge -e INITIAL_SLING_NODE=true -d -p 8080:8080 cards/cards
```

However, since runtime data isn't persisted after the container stops, no changes will be permanently persisted this way.
It is recommended to first create a permanent volume that can be reused between different image instantiations, and different image versions.

`docker volume create --label server=production cards-production-volume`

Then the container can be started with:

`docker container run --rm --network cardsbridge -e INITIAL_SLING_NODE=true --detach --volume cards-test-volume:/opt/cards/sling/ -p 8080:8080 --name cards-production cards/cards`

Explanation:

- `docker container run` creates and starts a new container
- `--rm` will automatically remove the container after it is stopped
- `--network cardsbridge` causes the container to connect to the network providing MongoDB
- `--detach` starts the container in the background
- `-e INITIAL_SLING_NODE=true` marks this container as the first to start up, and thus responsible for setting up the database
- `--volume cards-test-volume:/opt/cards/sling/` mounts the volume named `cards-test-volume` at `/opt/cards/sling/`, where the application data is stored
- `-p 8080:8080` makes the local port 8080 forward to the 8080 port inside the container
    - you can also specify a specific local network, and a different local port, for example `-p 127.0.0.1:9999:8080`
    - the second port must be `8080`
- `--name cards-production` gives a name to the container, for easy identification
- `cards/cards` is the name of the image

To enable developer mode, also add `--env DEV=true -p 5005:5005` to the `docker run` command.

To enable debug mode, also add `--env DEBUG=true` to the `docker run` command. Note that the application will not start until a debugger is actually attached to the process on port 5005.

`docker run --network cardsbridge -d -p 8080:8080 -p 5005:5005 -e INITIAL_SLING_NODE=true --env DEV=true --env DEBUG=true --name cards-debug cards/cards`

## Running with Docker-Compose

Docker-Compose can be employed to create a cluster of *N* MongoDB Shards, *M* MongoDB Replicas, and *one* CARDS instance.

### Installing/Starting

1. Before proceeding, ensure that the `cards/cards` Docker image has been built.

```bash
mvn clean install
```

2. The `ccmsk/neuralcr` image is also required. Please build it based on
the instructions available
at [https://github.com/ccmbioinfo/NeuralCR](https://github.com/ccmbioinfo/NeuralCR).
Use the **develop** branch.

Download the pre-trained NCR models from [here](https://github.com/ccmbioinfo/NeuralCR/releases/download/1.0/ncr_model_params.tar.gz)
and un-tar. Create the directory `NCR_MODEL` under `compose-cluster` and copy in the file `pmc_model_new.bin` along
with the directories `0` and `1` from the `ncr_model_params` directory.

3. Now build the *docker-compose* environment.

```bash
cd compose-cluster
python3 generate_compose_yaml.py --shards 2 --replicas 3
docker-compose build
```

3.1 Replacing `python3 generate_compose_yaml.py --shards 2 --replicas 3` with
`python3 generate_compose_yaml.py --oak_filesystem` will not start a Mongo
cluster and instead will use the file system as a data storage back-end for
Apache Sling

4. Start the *docker-compose* environment.

```bash
docker-compose up -d
```

5. The CARDS instance should be available at `http://localhost:8080/`

5.1. To inspect the data split between the MongoDB shards:
```bash
docker-compose exec router mongo
sh.status()
exit
```

### Stopping gracefully, without losing data

1. To stop the MongoDB/CARDS cluster:

```bash
docker-compose down
```

### Restarting

1. To restart the MongoDB/CARDS cluster while preserving the entered data
from the previous execution:

```bash
CARDS_RELOAD=true docker-compose up -d
```

### Cleaning up

1. To stop the MongoDB/CARDS cluster and **delete all entered data**:

```
docker-compose down #Stop all containers
docker-compose rm #Remove all stopped containers
docker volume prune -f #Remove all stored data
./cleanup.sh #Remove the cluster configuration files
```
