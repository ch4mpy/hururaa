# Runtime image for a Spring Boot module (gateway, hururaa-api). Build from the repository root:
#   docker build -f deploy/backend/Dockerfile --build-arg JAR=backend/gateway/target/gateway-*.jar .
# The jar is extracted into layers (dependencies first) so that rebuilding after a code change
# only pushes the application layer.
ARG JAVA_VERSION=26

FROM eclipse-temurin:${JAVA_VERSION}-jre AS extract
ARG JAR
WORKDIR /build
COPY ${JAR} app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

FROM eclipse-temurin:${JAVA_VERSION}-jre
ENV TZ="Pacific/Tahiti"
# Ubuntu publishes package fixes before Temurin rebuilds its image: take them without waiting.
# curl is for the compose healthcheck (the JRE image ships neither curl nor wget)
# The Ubuntu 26.04 base ships Canonical's pebble service manager outside dpkg (apt never updates
# it, so its Go CVEs fail the Trivy scan); this container runs java directly and never uses it.
RUN apt-get update \
    && apt-get upgrade -y \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && rm -f /usr/bin/pebble \
    && useradd --system --create-home --shell /usr/sbin/nologin spring
USER spring
WORKDIR /app
COPY --from=extract --chown=spring:spring /build/extracted/dependencies/ ./
COPY --from=extract --chown=spring:spring /build/extracted/spring-boot-loader/ ./
COPY --from=extract --chown=spring:spring /build/extracted/snapshot-dependencies/ ./
COPY --from=extract --chown=spring:spring /build/extracted/application/ ./
ENTRYPOINT ["java", "-jar", "app.jar"]
