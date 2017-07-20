FROM anapsix/alpine-java
ENV SCALA_VERSION=2.12.2 \
    SCALA_SHORT_VERSION=2.12 \
    SBT_VERSION=0.13.15\
    RANCHER_VERSION=v0.12.5
RUN apk add --update libstdc++ curl ca-certificates bash tar && \
    curl http://www.scala-lang.org/files/archive/scala-${SCALA_VERSION}.tgz > scala.tgz && \
    mkdir -p /opt/ && \
    tar xvfz scala.tgz -C /opt/ && \
    ln -s /opt/scala-${SCALA_VERSION}/bin/scala /usr/local/bin/scala
RUN curl -L https://github.com/sbt/sbt/releases/download/v${SBT_VERSION}/sbt-${SBT_VERSION}.tgz > sbt.tgz && \
    mkdir -p /opt/ && \
    tar xvfz sbt.tgz -C /opt/  && \
    ln -s /opt/sbt/bin/sbt /usr/local/bin/sbt
RUN mkdir -p /app
WORKDIR /app
COPY . /app
RUN sbt assembly

FROM anapsix/alpine-java
ENV SCALA_VERSION=2.12.2 \
    SCALA_SHORT_VERSION=2.12 \
    SBT_VERSION=0.13.15\
    RANCHER_VERSION=v0.12.5
RUN apk add --update libstdc++ curl ca-certificates bash tar && \
    curl http://www.scala-lang.org/files/archive/scala-${SCALA_VERSION}.tgz > scala.tgz && \
    mkdir -p /opt/ && \
    tar xvfz scala.tgz -C /opt/ && \
    ln -s /opt/scala-${SCALA_VERSION}/bin/scala /usr/local/bin/scala
RUN curl https://releases.rancher.com/compose/${RANCHER_VERSION}/rancher-compose-linux-amd64-${RANCHER_VERSION}.tar.gz > rancher-compose.tar.gz && \
    mkdir -p /opt/ && \
    tar xvfz rancher-compose.tar.gz -C /opt/ && \
    ln -s /opt/rancher-compose-${RANCHER_VERSION}/rancher-compose /usr/local/bin/rancher-compose
RUN mkdir -p /app
COPY --from=0 /app/target/scala-${SCALA_SHORT_VERSION}/rancher-lb-configurator-assembly-1.0.jar /app/rancherlbconfigurator.jar
CMD ["/usr/local/bin/scala", "/app/rancherlbconfigurator.jar"]