ARG KC_VERSION=26.7
ARG CN=host.docker.internal
ARG KC_HEALTH_ENABLED=true
ARG KC_METRICS_ENABLED=true
ARG KC_HTTP_ENABLED=false
ARG KC_HTTPS_PORT=3643
ARG KC_LOG_LEVEL=INFO

FROM quay.io/keycloak/keycloak:${KC_VERSION}

ARG CN
ARG KC_HOSTNAME
ARG KC_HEALTH_ENABLED
ARG KC_METRICS_ENABLED
ARG KC_HTTP_ENABLED
ARG KC_HTTPS_PORT
ARG KC_LOG_LEVEL

ENV KC_HTTP_RELATIVE_PATH=/auth/
ENV KC_HOSTNAME=https://${CN}/auth/
ENV KC_HOSTNAME_ADMIN=https://${CN}/auth/
ENV KC_HEALTH_ENABLED=$KC_HEALTH_ENABLED
ENV KC_METRICS_ENABLED=$KC_METRICS_ENABLED
ENV KC_HTTP_ENABLED=$KC_HTTP_ENABLED
ENV KC_HTTPS_PORT=$KC_HTTPS_PORT
ENV KC_LOG_LEVEL=$KC_LOG_LEVEL

ENV TZ="Pacific/Tahiti"

USER root 

COPY --chown=keycloak:keycloak ./certs/${CN}.crt /tmp/${CN}.crt
COPY --chown=keycloak:keycloak ./keycloak/themes/ /opt/keycloak/themes/

# Trust the self-signed certificate, so that Keycloak can reach the other dev-infra containers over
# TLS. Only the certificate is imported, never the key pair, so no private key material is left in
# any image layer. Trivy's secret
# scanner fails the deployment pipeline on a bundled private key, and a key shipped in a published
# image is worth avoiding even when it is a throw-away one. The server's own TLS material is
# mounted at run time instead (see the `certs` volumes of compose-keycloak.yml); the demo
# deployment needs none of it, it serves plain HTTP behind Caddy.
RUN keytool -importcert -noprompt -alias ${CN} -file /tmp/${CN}.crt -cacerts -storepass changeit \
    && rm -f /tmp/${CN}.crt

RUN mkdir -p /opt/keycloak/data/h2 && chown -R keycloak:root /opt/keycloak/data/h2 && chmod -R 775 /opt/keycloak/data/h2

USER keycloak

RUN /opt/keycloak/bin/kc.sh build
