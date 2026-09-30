#!/usr/bin/env bash
source ./.env
bash ./certs/add-to-jre-cacerts.sh ~/.ssh/$CN.jks $(cat ./secrets/ssl/password.txt) $CN