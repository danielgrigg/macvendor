#!/usr/bin/env bash

function exitWithUsage {
  echo "Usage: ./destroy.sh <param-file>"
  exit 1
}

paramFile="$1"
[[ -f "$paramFile" ]] || { echo "Param file missing."; exitWithUsage; }
. "$paramFile"

[[ -n $digitaloceanAccessToken ]] || { echo "digitaloceanAccessToken not set in $paramFile"; exitWithUsage; }
[[ -n $domainName ]] || { echo "domainName not set in $paramFile"; exitWithUsage; }

machineName="macvendor-$domainName"

docker-machine rm $machineName

curl -sS -X DELETE -H "Content-Type: application/json" -H "Authorization: Bearer $digitaloceanAccessToken" "https://api.digitalocean.com/v2/domains/$domainName"
echo
curl -sS -X DELETE -H "Content-Type: application/json" -H "Authorization: Bearer $digitaloceanAccessToken" "https://api.digitalocean.com/v2/domains/www.$domainName"
echo
