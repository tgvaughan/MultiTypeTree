# Dockerfile to build container for unit testing

FROM openjdk:8

RUN apt-get update && apt-get install -y git ant libgfortran3

ADD . /root/
