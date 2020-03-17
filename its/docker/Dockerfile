FROM gcr.io/ci-cd-215716/base:latest

USER root

RUN apt-get update && apt-get -y install xvfb

ENV JAVA_HOME /usr/java/openjdk-12
#ENV PATH $JAVA_HOME/bin:$PATH

ENV JAVA_VERSION 12
ENV JAVA_URL https://download.java.net/java/GA/jdk12/GPL/openjdk-12_linux-x64_bin.tar.gz
ENV JAVA_SHA256 b43bc15f4934f6d321170419f2c24451486bc848a2179af5e49d10721438dd56

RUN set -eux \
 	&& curl -fL -o /openjdk.tgz "$JAVA_URL" \
 	&& echo "$JAVA_SHA256 */openjdk.tgz" | sha256sum -c - \
 	&& mkdir -p "$JAVA_HOME" \
 	&& tar --extract --file /openjdk.tgz --directory "$JAVA_HOME" --strip-components 1 \
 	&& rm /openjdk.tgz


# Back to the user of the base image
USER sonarsource

