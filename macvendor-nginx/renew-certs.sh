#/bin/sh

certbot renew --non-interactive --pre-hook 'supervisorctl stop nginx' --post-hook 'supervisorctl start nginx'

