# Dockerfile to build container for unit testing

FROM debian:stable

RUN apt-get update
RUN apt-get install -y openjdk-11-jdk
RUN apt-get install -y git
RUN apt-get install -y ant
RUN apt-get install -y libgfortran4

WORKDIR /root

ADD . ./

ENTRYPOINT ant test
