FROM maven:3.9-amazoncorretto-26 AS build
WORKDIR /app
COPY pom.xml .
COPY shared-models shared-models/
COPY account-service account-service/
RUN mvn package -pl account-service -am -DskipTests

FROM amazoncorretto:26
WORKDIR /app
COPY --from=build /app/account-service/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "--enable-preview", "-jar", "app.jar"]
