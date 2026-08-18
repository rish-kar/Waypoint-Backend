FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean verify

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S waypoint && adduser -S waypoint -G waypoint
WORKDIR /app
COPY --from=build /app/target/waypoint-backend-*.jar app.jar
USER waypoint
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]