# syntax=docker/dockerfile:1.7

ARG JAVA_VERSION=21
ARG MAVEN_VERSION=3.9.16

FROM maven:${MAVEN_VERSION}-eclipse-temurin-${JAVA_VERSION} AS build
WORKDIR /workspace

COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests dependency:go-offline

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests package \
    && install -D -m 0444 target/lsp4j-mcp-1.0.0-SNAPSHOT.jar /out/lsp4j-mcp.jar

FROM alpine:3.22 AS jdtls
ARG JDTLS_VERSION=1.60.0
ARG JDTLS_BUILD=202606262232
ARG JDTLS_SHA256=e94c303d8198f977930803582738771fd18c52c5492878410bf222b1aa81ef1d
ARG JDTLS_ARCHIVE=jdt-language-server-${JDTLS_VERSION}-${JDTLS_BUILD}.tar.gz
ARG JDTLS_URL=https://download.eclipse.org/jdtls/milestones/${JDTLS_VERSION}/${JDTLS_ARCHIVE}

ADD --checksum=sha256:${JDTLS_SHA256} ${JDTLS_URL} /tmp/jdtls.tar.gz
RUN mkdir -p /opt/jdtls \
    && tar -xzf /tmp/jdtls.tar.gz -C /opt/jdtls \
    && chmod 0555 /opt/jdtls/bin/jdtls /opt/jdtls/bin/jdtls.py

FROM eclipse-temurin:${JAVA_VERSION}-jre-jammy AS runtime

LABEL org.opencontainers.image.title="LSP4J MCP Server" \
      org.opencontainers.image.description="MCP server exposing Java language intelligence through Eclipse JDTLS" \
      org.opencontainers.image.source="https://github.com/jabrena/LSP4J-MCP" \
      org.opencontainers.image.version="1.0.0-SNAPSHOT"

RUN apt-get update \
    && apt-get install --yes --no-install-recommends python3 \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 mcp \
    && useradd --uid 10001 --gid 10001 --no-create-home \
        --home-dir /tmp/mcp --shell /usr/sbin/nologin mcp \
    && install -d -o 10001 -g 10001 /opt/lsp4j-mcp /workspace

COPY --from=build --chown=10001:10001 /out/lsp4j-mcp.jar /opt/lsp4j-mcp/lsp4j-mcp.jar
COPY --from=jdtls --chown=10001:10001 /opt/jdtls/ /opt/jdtls/
COPY --chmod=0555 docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh

ENV HOME=/tmp/mcp \
    JDTLS_CMD="jdtls --jvm-arg=-Xms128m --jvm-arg=-XX:MaxRAMPercentage=60.0 --jvm-arg=-XX:+ExitOnOutOfMemoryError" \
    LOG_FILE=/dev/stderr \
    PATH="/opt/jdtls/bin:${PATH}" \
    PYTHONDONTWRITEBYTECODE=1

WORKDIR /workspace
USER 10001:10001
STOPSIGNAL SIGTERM

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["/workspace"]
