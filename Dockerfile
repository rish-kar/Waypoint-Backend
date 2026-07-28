FROM maven:3.9.14-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system waypoint && useradd --system --gid waypoint --home-dir /app waypoint
COPY --from=build --chown=waypoint:waypoint /workspace/target/waypoint-backend-0.0.1-SNAPSHOT.jar app.jar
USER waypoint
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
