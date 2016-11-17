# macvendor

A simple but complete restful service to query the vendor
for a MAC address.

# Getting Started

You can either deploy the whole service to a digitalocean 
host or just run macvendor service itself for demo purposes.

To run macvendor independently:

$ sbt run

or just run the latest published container:

$ docker run --rm -p8080:8080 --name macvendor1 danielgrigg/macvendor

For the former there's a provision.sh script to 
deploy a digitalocean host against a domain you administer.  It 
also uses docker-compose to run the macvendor and an nginx
frontend container. The nginx is used as a reverse proxy with ssl termination. 
The nginx image is configured to configure itself with a letsencrypt certificate
on startup for your domain.  You wouldn't want to use this in the real
world since letsencrypt certificates expire in 90 days at time of writing
and the letsencrypt rate limits (20 / week) make treating such 
certifiates as disposable as docker containers a risky proposition. Any
PR to support other hosts, deploy with ansible or something are welcome.




