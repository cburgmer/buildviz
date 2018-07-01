FROM openjdk:alpine

ENV BUILDVIZ_SERVER_DIR="/buildviz"

RUN addgroup buildviz && \
    adduser -D -G buildviz -h ${BUILDVIZ_SERVER_DIR} -s /bin/sh buildviz

EXPOSE 3000
USER buildviz
WORKDIR /buildviz

ADD run.sh ${BUILDVIZ_SERVER_DIR}/
ADD buildviz-*-standalone.jar ${BUILDVIZ_SERVER_DIR}/

ENTRYPOINT ["/buildviz/run.sh"]
CMD ["server"]
