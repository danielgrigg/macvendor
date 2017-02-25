# What is macvendor-nginx?

This image augments a plain nginx image to fetch a letsencrypt certificate and 
install it on startup. It is only intended for usage with the (macvendor image)[https://hub.docker.com/r/danielgrigg/macvendor/].
The source for everything can be found at (macvendor source)[https://github.com/danielgrigg/macvendor].

The image includes a cron job to automatically renew the certificate before it expires.

# How to use this image?

Use it in conjunction with the macvendor image.
