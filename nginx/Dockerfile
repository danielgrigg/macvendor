FROM nginx

MAINTAINER danielgrigg

RUN echo 'deb http://ftp.debian.org/debian jessie-backports main' >> /etc/apt/sources.list.d/sources.list
RUN apt-get update && apt-get install -y certbot -t jessie-backports

RUN mkdir /var/www/

COPY index.html /var/www/
COPY nginx.conf.template start-nginx.sh ./

EXPOSE 80 443

CMD /bin/bash start-nginx.sh
