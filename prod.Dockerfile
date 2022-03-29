FROM adoptopenjdk/openjdk11:jdk-11.0.10_9-alpine-slim AS stage1

ENV SBT_VERSION "1.6.2"
ENV INSTALL_DIR /usr/local
ENV SBT_HOME /usr/local/sbt
ENV PATH "${PATH}:${SBT_HOME}/bin"
ENV SBT_URL "https://github.com/sbt/sbt/releases/download/v$SBT_VERSION/sbt-$SBT_VERSION.tgz"

RUN set -o pipefail && \
    apk update && \
    apk add --upgrade apk-tools && \
    apk upgrade --available && \
    apk add --no-cache --update bash wget npm git openssh && \
    mkdir -p "$SBT_HOME" && \
    wget -qO - "${SBT_URL}" | tar xz -C "${INSTALL_DIR}" && \
    echo -ne "- with sbt $SBT_VERSION\n" >> /root/.built

ENV PROJECT_HOME /usr/src
ENV PROJECT_NAME universal-application-tool-0.0.1
ENV PROJECT_LOC "${PROJECT_HOME}/${PROJECT_NAME}"

COPY "${PROJECT_NAME}"
RUN cd "${PROJECT_LOC}" && \
    npm install -g npm@8.5.1 && \
    npm install && \
    sbt update && \
    sbt dist && \
    mv "${PROJECT_LOC}/target/universal/universal-application-tool-0.0.1.zip" /civiform.zip && \
    unzip /civiform.zip &&
    chmod +x /universal-application-tool-0.0.1/bin/universal-application-tool

# This is a common trick to shrink container sizes. We discard everything added
# during the build phase and use only the inflated artifacts created by sbt dist.
FROM adoptopenjdk/openjdk11:jdk-11.0.10_9-alpine-slim AS stage2
COPY --from=stage1 /universal-application-tool-0.0.1 /universal-application-tool-0.0.1

# Upgrade packages for stage2 to include latest versions.
RUN set -o pipefail && \
    apk update && \
    apk add --upgrade apk-tools && \
    apk upgrade --available && \
    apk add --no-cache --update bash openssh

ARG image_tag
ENV CIVIFORM_IMAGE_TAG=$image_tag

RUN apk add bash

CMD ["/universal-application-tool-0.0.1/bin/universal-application-tool", "-Dconfig.file=/universal-application-tool-0.0.1/conf/application.conf"]
