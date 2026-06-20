FROM maven:3.9-amazoncorretto-26 AS build
WORKDIR /app
COPY pom.xml .
COPY shared-models shared-models/
COPY event-gateway event-gateway/
RUN mvn package -pl event-gateway -am -DskipTests

FROM amazoncorretto:26
WORKDIR /app
COPY --from=build /app/event-gateway/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "--enable-preview", "-jar", "app.jar"]
