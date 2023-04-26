FROM registry.access.redhat.com/ubi8/openjdk-8-runtime:1.15-1.1682399166

COPY target/csv-service-*.jar /deployments/

EXPOSE 8080 8443

