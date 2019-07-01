# Dockerfile to build container for unit testing

FROM debian:stable

RUN apt-get update && apt-get install -y openjdk-8-jdk git ant libgfortran3

WORKDIR /root

ADD . ./

ENTRYPOINT ant test
