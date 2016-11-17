#!/usr/bin/env bash

if [[ -z $digitaloceanAccessToken ]]; then
  echo "Usage: provision.sh <digitalocean-access-token> <domain-name>"
  exit 1
fi
if [[ -z $domainName ]]; then
  echo "Usage: provision.sh <digitalocean-access-token> <domain-name>"
  exit 1
fi
if [[ -z $region ]]; then
  echo "Usage: provision.sh <digitalocean-access-token> <domain-name> <region>"
  exit 1
fi

machineName="macvendor-$domainName"

docker-machine create --driver digitalocean --digitalocean-access-token $digitaloceanAccessToken --digitalocean-image=debian-8-x64 --digitalocean-region="$region" --digitalocean-size=512mb "$machineName"

machineIp=$(docker-machine ip $machineName)

curl -X POST -H "Content-Type: application/json" -H "Authorization: Bearer $digitaloceanAccessToken" -d "{\"name\":\"$domainName\",\"ip_address\":\"$machineIp\"}" "https://api.digitalocean.com/v2/domains"
echo
curl -X POST -H "Content-Type: application/json" -H "Authorization: Bearer $digitaloceanAccessToken" -d "{\"name\":\"www.$domainName\",\"ip_address\":\"$machineIp\"}" "https://api.digitalocean.com/v2/domains"
echo

docker-compose up -d
