FROM maven:3.6-jdk-8 as builder

COPY benchmark /workspace

WORKDIR /workspace

RUN mvn package

FROM openjdk:11.0-jre

WORKDIR /rabbittesttool

COPY --from=builder \
    /workspace/target/rabbittesttool-1.1-SNAPSHOT-jar-with-dependencies.jar \
    rabbittesttool.jar

COPY benchmark/topologies topologies
COPY benchmark/policies policies

ENTRYPOINT ["java", "-jar", "rabbittesttool.jar"]