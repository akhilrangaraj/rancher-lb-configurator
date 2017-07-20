#Rancher LB Configuration service

##What this service does
This service scans the rancher metadata service for containers with the label `com.casetext.dns_name` and configures the rancher loadbalancer to route traffic to the container for that dns name. Think k8s ingress controller, but less cool.

##How to use it
First build the image and push to a repo. The dockerfile requires the latest docker for the multistage build features.
Deploy to rancher with api keys:

```yaml
version: '2'
services:
  rancher-configurator:
    image: {IMAGEREPO}
    environment:
      RANCHER_ACCESS_KEY: {ACCESSKEY}
      RANCHER_SECRET_KEY: {SECRETKEY}
      RANCHER_SERVER: {RANCHERAPISERVER}
    labels:
      io.rancher.container.pull_image: always

```

On any service that you wish to deploy, have the following label:
```yaml
    labels: 
        com.casetext.dns_name: {DNSENTRY}
```

Sit back and watch the magic.

##TODOS:
- configurable refresh times
- read secrets using the rancher secrets api
- path based routing