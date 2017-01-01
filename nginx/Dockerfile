FROM nginx

MAINTAINER danielgrigg

RUN echo 'deb http://ftp.debian.org/debian jessie-backports main' >> /etc/apt/sources.list.d/sources.list
RUN apt-get update && apt-get install -y supervisor cron certbot -t jessie-backports

COPY renew-certs-crontab /etc/cron.d/renew-certs
RUN chmod 0644 /etc/cron.d/renew-certs && touch /var/log/cron.log
COPY renew-certs.sh ./

RUN mkdir -p /var/www/ /data/nginx/cache 
COPY index.html /var/www/
COPY nginx.conf.template start-nginx.sh ./

RUN mkdir -p /var/lock/nginx /var/run/nginx /var/log/supervisor
COPY supervisord.conf /etc/supervisor/conf.d/supervisord.conf

EXPOSE 80 443

CMD /bin/bash start-nginx.sh
