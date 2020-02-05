FROM mongo:4.2-bionic

COPY mongo-shard.conf /etc/mongo.conf
ENTRYPOINT mongod --config /etc/mongo.conf
