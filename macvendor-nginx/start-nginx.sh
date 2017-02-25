#!/bin/bash

[[ -n $domainName ]] || { echo "domainName must be set - aborting."; exit 1; }
[[ -n $contactEmail ]] || { echo "contactEmail must be set - aborting."; exit 1; }

echo "Certbotting $domainName..."
certbot --non-interactive --text certonly --agree-tos --email "$contactEmail" --standalone -d $domainName -d www.$domainName
sed "s/VAR_DOMAIN/$domainName/g" nginx.conf.template > /etc/nginx/nginx.conf

supervisord
