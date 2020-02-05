FROM mongo:4.2-bionic

COPY mongo-configdb.conf /etc/mongo.conf
ENTRYPOINT mongod --config /etc/mongo.conf
