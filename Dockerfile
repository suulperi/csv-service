FROM registry.access.redhat.com/ubi8/openjdk-17-runtime:1.15-1.1682053056

COPY src/target/csv-service-*.jar /deployments/

EXPOSE 8080 8443

