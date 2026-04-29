FROM ubuntu:latest
LABEL authors="elisa"

ENTRYPOINT ["top", "-b"]