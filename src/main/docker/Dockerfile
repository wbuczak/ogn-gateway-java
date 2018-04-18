FROM anapsix/alpine-java:8_server-jre

RUN apk --no-cache add procps

ENV OGN_HOME=/opt/ogn \
    JAVA_HOME=/opt/jdk/jre

RUN mkdir -pv \
             ${OGN_HOME} \
             ${OGN_HOME}/ogn-gateway

ADD ${project.build.finalName}-all.tar.gz loop.sh ${OGN_HOME}/ogn-gateway/

WORKDIR ${OGN_HOME}/ogn-gateway

EXPOSE 61628

RUN echo -e \
      "ognjmx readonly" > conf/jmxremote.access \
      "\nognadm readwrite" >> conf/jmxremote.access && \
    echo -e \
      "ognjmx ogn" > conf/jmxremote.passwd \
      "\nognadm ogn" >> conf/jmxremote.passwd && \
    chmod 600 conf/jmxremote.* && \
    chmod 600 conf/log4j.xml && \
    chmod +x loop.sh

ENTRYPOINT ./ogn-gateway.sh start && ./loop.sh
