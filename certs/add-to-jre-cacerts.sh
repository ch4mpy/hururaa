#!/usr/bin/env bash
# Imports a self-signed certificate into the cacerts of the JDK at JAVA_HOME. Non-interactive:
# called without a terminal by build-0-env.sh and reset-dev-env.sh. Overrides through the
# environment: JAVA_HOME (required) and CACERTS_PASSWORD (defaults to the JDK's "changeit").
set -euo pipefail

CERTIFICATE=${1:-}
if [ ! -f "${CERTIFICATE}" ]; then
  echo "Usage: add-to-jre-cacerts.sh CERTIFICATE SSL_PASSWORD SRC_ALIAS DEST_ALIAS"
  echo "The certificate CERTIFICATE is mandatory and must exist."
  echo "SSL_PASSWORD is the password for the source key and keystore. It defaults to \"secret\""
  echo "SRC_ALIAS is the alias for the certificate in the source keystore. It defaults to \"host.docker.internal\""
  echo "DEST_ALIAS is the alias for the certificate in the cacerts file. It defaults to \"hururaa\""
  exit 1
fi

SSL_PASSWORD=${2:-${SERVER_SSL_KEY_PASSWORD:-secret}}
SRC_ALIAS=${3:-host.docker.internal}
DEST_ALIAS=${4:-${SRC_ALIAS}}

JAVA=${JAVA_HOME:-}
JAVA=$(echo "$JAVA" | sed 's/\\/\//g')
if [ -z "${JAVA}" ]; then
  echo "ERROR: JAVA_HOME is not set, cannot locate the JDK / JRE whose cacerts to update"
  exit 1
fi
# Locate cacerts file
if [ -f "${JAVA}/lib/security/cacerts" ]; then
  # recent JDKs and JREs style
  CACERTS=("${JAVA}/lib/security/cacerts")
elif [ -f "${JAVA}/jre/lib/security/cacerts" ]; then
  # legacy JDKs style (1.8 and older)
  CACERTS=("${JAVA}/jre/lib/security/cacerts")
else
  echo "ERROR: could not locate cacerts under $JAVA"
  exit 1
fi

CACERTS_PASSWORD=${CACERTS_PASSWORD:-changeit}

echo "Importing certificate into ${CACERTS}..."
# -noprompt: keytool would otherwise ask whether to overwrite an alias already present (the case
# after any earlier run), which is the third question this script used to stop on
"${JAVA}/bin/keytool" -importkeystore -noprompt -srckeystore "${CERTIFICATE}" -srckeypass "${SSL_PASSWORD}" -srcstorepass "${SSL_PASSWORD}" -srcstoretype pkcs12 -srcalias "${SRC_ALIAS}" -destkeystore "${CACERTS}" -deststorepass "${CACERTS_PASSWORD}" -destalias "${DEST_ALIAS}"