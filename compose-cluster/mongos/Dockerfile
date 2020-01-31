FROM mongo

#Need netcat to signal LFS to start
RUN apt-get update
RUN apt-get install -y netcat-traditional

COPY initialize_mongos.sh /initialize_mongos.sh
RUN chmod +x /initialize_mongos.sh
COPY mongo.conf /etc/mongo.conf
ENTRYPOINT /initialize_mongos.sh
