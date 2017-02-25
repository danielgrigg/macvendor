# macvendor

A simple but complete restful service to query the vendor
for a MAC address.

# Getting Started

You can either deploy the whole service to a digitalocean 
host or just run macvendor service itself for demo purposes.

## Running

If you just want to run the latest published image:

```bash
$ docker run --rm -p8080:8080 --name macvendor1 danielgrigg/macvendor
```

Otherwise you can run it locally with sbt:

```bash
$ sbt run
```

or even build the image locally using the sbt-docker plugin then run it:

```bash
$ sbt docker
$ docker run --rm -p8080:8080 --name macvendor1 danielgrigg/macvendor
```

## Publishing

macvendor uses the [sbt-docker plugin](https://github.com/marcuslonnberg/sbt-docker) to simplify
building and publishing an image with all the dependencies baked in.


## Provisioning

There's a provision.sh script to deploy a digitalocean host against a domain 
you administer.  It uses docker-compose to launch a macvendor container and 
an nginx container. The nginx is used as a reverse proxy with ssl termination. 

The nginx image is configured to configure itself with a letsencrypt certificate
on startup for your domain. Of course even letsencrypt certificates 
aren't as disposable as containers so you shouldn't use this approach
in production but for pedagogical purposes it's pretty neat. 


