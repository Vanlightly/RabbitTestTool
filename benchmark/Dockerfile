FROM openjdk:11-jre-slim

COPY target/rabbittesttool-1.1-SNAPSHOT-jar-with-dependencies.jar ./
COPY topologies ./
COPY policies ./

ENTRYPOINT ["java", "-jar", "rabbittesttool-1.1-SNAPSHOT-jar-with-dependencies.jar"]
