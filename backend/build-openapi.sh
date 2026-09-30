cd "$(dirname "$0")/"
set -a
source ../.env
set +a
bash ./mvnw clean verify -Popenapi,h2 -DskipTests