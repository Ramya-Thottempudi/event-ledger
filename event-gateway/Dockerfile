FROM maven:3.9.16-eclipse-temurin-26 AS build
WORKDIR /app
COPY pom.xml .
COPY shared-models shared-models/
COPY event-gateway event-gateway/
COPY account-service/pom.xml account-service/pom.xml
RUN mvn package -pl event-gateway -am -DskipTests

FROM eclipse-temurin:26.0.1_8-jdk
RUN apt-get update -qq && apt-get install -y -qq wget && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/event-gateway/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "--enable-preview", "-jar", "app.jar"]
