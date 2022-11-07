# Dockerfile to build container for unit testing

FROM debian:stable

RUN apt-get update
RUN apt-get install -y openjdk-17-jdk openjfx
RUN apt-get install -y ant
RUN apt-get install -y jblas

WORKDIR /root

ADD . ./

RUN rm lib/jblas-1.2.4.jar
RUN ln -s /usr/share/java/jblas.jar lib/jblas.jar

ENTRYPOINT JAVA_FX_HOME=/usr/share/java/ ant test
