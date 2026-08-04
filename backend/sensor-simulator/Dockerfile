# sensor-simulator — ARCHITECTURE.md section 8.2
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .
RUN mvn -B -pl backend/sensor-simulator -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S cuas && adduser -S cuas -G cuas
WORKDIR /app
COPY --from=build /build/backend/sensor-simulator/target/*.jar app.jar
USER cuas
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
