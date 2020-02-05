FROM mongo:4.2-bionic

COPY initialize_all.sh /initialize_all.sh
RUN chmod +x /initialize_all.sh

COPY wait_for_mongo.sh /wait_for_mongo.sh
RUN chmod +x /wait_for_mongo.sh

COPY mongo_rs_initiate.sh /mongo_rs_initiate.sh
RUN chmod +x /mongo_rs_initiate.sh

COPY mongo_add_shard.sh /mongo_add_shard.sh
RUN chmod +x /mongo_add_shard.sh

ENTRYPOINT /initialize_all.sh
