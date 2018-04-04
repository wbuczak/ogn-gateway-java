FROM anapsix/alpine-java:8_server-jre

RUN apk --no-cache add procps

#RUN echo \ 
#  "export PS1='\u@\h:\w\$ '" > /etc/profile.d/prompt.sh

ENV OGN_HOME=/opt/ogn \
    JAVA_HOME=/opt/jdk/jre

RUN mkdir -pv \
             ${OGN_HOME} \
             ${OGN_HOME}/ogn-gateway

ADD ${project.build.finalName}-all.tar.gz ${OGN_HOME}/ogn-gateway

WORKDIR ${OGN_HOME}/ogn-gateway

EXPOSE 61628

RUN echo -e \
      "ognjmx readonly" > conf/jmxremote.access \
      "\nognadm readwrite" >> conf/jmxremote.access && \          
    echo -e \
      "ognjmx ogn" > conf/jmxremote.passwd \
      "\nognadm ogn" >> conf/jmxremote.passwd && \            
    chmod 600 conf/jmxremote.* && \
    chmod 600 conf/log4j.xml
           

#RUN chmod +rx /opt/vc/${project.artifactId}/bin/docker-entrypoint.sh
#ENTRYPOINT ["/bin/bash","/opt/ogn/ogn-gateway/ogn-gateway.sh"]
#CMD ["start"]