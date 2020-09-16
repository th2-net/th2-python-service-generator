FROM openjdk:12-alpine
WORKDIR /home
COPY ./ .
ENTRYPOINT ["/home/th2-python-service-generator/bin/th2-python-service-generator"]