FROM openjdk:8

ENV BUILDVIZ_SERVER_DIR="/buildviz" \
    BUILDVIZ_JAR="buildviz-*-standalone.jar"

RUN groupadd buildviz && \
    useradd -m -d "${BUILDVIZ_SERVER_DIR}" -g buildviz -s /bin/sh buildviz

EXPOSE 3000
USER buildviz
WORKDIR /buildviz

ADD run.sh ${BUILDVIZ_SERVER_DIR}/
ADD ${BUILDVIZ_JAR} ${BUILDVIZ_SERVER_DIR}/

ENTRYPOINT ["/buildviz/run.sh"]
CMD ["server"]
