#!/bin/bash

if [[ -z $domainName ]]; then
  echo "domainName must be set - aborting."
  exit 1
fi

echo "Certbotting $domainName..."
certbot --non-interactive --text certonly --agree-tos --email 'mail@danielgrigg.com' --standalone -d $domainName -d www.$domainName
sed "s/VAR_DOMAIN/$domainName/g" nginx.conf.template > /etc/nginx/nginx.conf
echo "Starting nginx..."
nginx -g 'daemon off;'

