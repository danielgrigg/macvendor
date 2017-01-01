#!/usr/bin/env bash

function exitWithUsage { 
  echo "Usage: ./provision.sh <param-file>"
  exit 1
}

paramFile="$1"
[[ -f "$paramFile" ]] || { echo "Param file missing."; exitWithUsage; }
. "$paramFile"
[[ -n $digitaloceanAccessToken ]] || { echo "digitaloceanAccessToken not set in $paramFile"; exitWithUsage; }
[[ -n $domainName ]] || { echo "domainName not set in $paramFile"; exitWithUsage; }
[[ -n $contactEmail ]] || { echo "contactEmail not set in $paramFile"; exitWithUsage; }
[[ -n $region ]] || { echo "region not set in $paramFile"; exitWithUsage; }


machineName="macvendor-$domainName"

if docker-machine ls -q | grep --quiet $machineName; then
  echo "Machine $machineName already exists - aborting."
  exit 1
fi

echo "Creating digitalocean machine $machineName in $region..."
docker-machine create --driver digitalocean --digitalocean-access-token $digitaloceanAccessToken --digitalocean-image=debian-8-x64 --digitalocean-region="$region" --digitalocean-size=512mb "$machineName"

machineIp=$(docker-machine ip "$machineName")

eval $(docker-machine env "$machineName")

echo "Adding DNS for $machineName to $machineIp"
curl -X POST -H "Content-Type: application/json" -H "Authorization: Bearer $digitaloceanAccessToken" -d "{\"name\":\"$domainName\",\"ip_address\":\"$machineIp\"}" "https://api.digitalocean.com/v2/domains"; echo
curl -X POST -H "Content-Type: application/json" -H "Authorization: Bearer $digitaloceanAccessToken" -d "{\"type\":\"A\",\"name\":\"@\",\"data\":\"$machineIp\",\"priority\":null,\"port\":null,\"weight\":null}" "https://api.digitalocean.com/v2/domains/$domainName/records"; echo
curl -X POST -H "Content-Type: application/json" -H "Authorization: Bearer $digitaloceanAccessToken" -d "{\"name\":\"www.$domainName\",\"ip_address\":\"$machineIp\"}" "https://api.digitalocean.com/v2/domains"; echo
curl -X POST -H "Content-Type: application/json" -H "Authorization: Bearer $digitaloceanAccessToken" -d "{\"type\":\"A\",\"name\":\"@\",\"data\":\"$machineIp\",\"priority\":null,\"port\":null,\"weight\":null}" "https://api.digitalocean.com/v2/domains/www.$domainName/records"; echo

docker-compose up -d
