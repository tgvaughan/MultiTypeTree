# Dockerfile to build container for unit testing

FROM debian:stable

RUN apt-get update
RUN apt-get install -y openjdk-21-jdk openjfx ant
RUN apt-get install -y jblas

WORKDIR /root

ADD . ./

RUN rm lib/jblas-*.jar
RUN ln -s /usr/share/java/jblas.jar lib/jblas.jar

ENTRYPOINT JAVA_FX_HOME=/usr/share/java/ ant test
