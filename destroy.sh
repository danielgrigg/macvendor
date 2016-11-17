#!/usr/bin/env bash

if [[ -z $digitaloceanAccessToken ]]; then
  echo "Usage: provision.sh <digitalocean-access-token> <domain-name>"
  exit 1
fi
if [[ -z $domainName ]]; then
  echo "Usage: provision.sh <digitalocean-access-token> <domain-name>"
  exit 1
fi

machineName="macvendor-$domainName"

docker-machine rm $machineName

curl -sS -X DELETE -H "Content-Type: application/json" -H "Authorization: Bearer $digitaloceanAccessToken" "https://api.digitalocean.com/v2/domains/$domainName"
echo
curl -sS -X DELETE -H "Content-Type: application/json" -H "Authorization: Bearer $digitaloceanAccessToken" "https://api.digitalocean.com/v2/domains/www.$domainName"
echo
