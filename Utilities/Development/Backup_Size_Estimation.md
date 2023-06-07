1. Start a simple Mongo DB Docker container

```bash
docker run --rm -p 27017:27017 -it mongo:6.0-jammy
```

2. Build a Docker Compose environment using the Mongo DB container as a storage backend

```bash
cd compose-cluster
./cleanup.sh
python3 generate_compose_yaml.py --external_mongo --external_mongo_address 172.99.0.1 --external_mongo_dbname sling --cards_project cards4proms --dev_docker_image --composum --smtps --subnet 172.99.0.0/16
docker-compose build
docker-compose up -d
```

3. Start the `mock_torch.js` server to serve randomly generated patients

```bash
cd Utilities/Development
nodejs mock_torch.js --appointment-time-hours-from-now=24 --nRandomPatients=90
```

4. Visit `http://localhost:8080/system/console/configMgr` and configure _PROMs import_
  - `Name`: `LocalNodeJS`
  - `Import schedule`: `0 0 0 * * ? *`
  - `days to query`: `3`
  - `Endpoint URL`: `http://172.99.0.1:8011/`
  - `Authentication URL`: (leave blank)
  - `Vault token`: (leave blank)
  - `Clinic names`: `6012-HC-Congenital Cardiac`
  - `Provider names`: (leave blank)
  - `Vault role name`: (leave blank)

5. Import the randomly generated patients and visits from the Mock Torch server by visiting `http://localhost:8080/Subjects.importTorch?config=LocalNodeJS`

6. Wait for the Torch import to finish. When it is finished, the CARDS dashboard will show 90 loaded _Patient_ subjects.

7. Load the survey responses

```bash
cd Utilities/Development
python3 auto_answer_surveys.py Cards4Proms-SampleAnswers/AUDITC.csv Cards4Proms-SampleAnswers/EQ5D.csv Cards4Proms-SampleAnswers/GAD7.csv Cards4Proms-SampleAnswers/PHQ9.csv Cards4Proms-SampleAnswers/SC.csv
```

8. Measure the size of a `mongodump` backup

```bash
docker ps # and get the name of the Mongo DB Docker container
docker exec -it NAME_OF_MONGO_DB_DOCKER_CONTAINER /bin/bash
mongodump --archive | wc --bytes

# Or if using a single-node replica set, as is what should be done in
# production use:
# mongodump --archive --oplog | wc --bytes
```

For an import of 90 randomly generated patients each with a visit
happening tomorrow with 5 completed clinical forms, plus the
_Visit information_ form, plus the _patient information_ form, we have
a total of `438235761` bytes or approximately 418 MB. For the version
that uses `--oplog`, this value was `504138955` bytes or approximately
481 MB. This backup, gzipped is `107776172` bytes or approximately
103 MB.

9. Shutdown and cleanup

```bash
cd compose-cluster
docker-compose down
docker-compose rm
docker volume prune -f
./cleanup.sh
```

10. To restore a backup, within the Mongo DB container run:

```bash
mongorestore --archive < backup.dump

# Or if using a single-node replica set, as is what should be done in
# production use:
mongorestore --archive --oplogReplay < mongo_backup_2022-05-02.dump
```
