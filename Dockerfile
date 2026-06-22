# --- Stage 1: Build source code ---
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
COPY sql ./sql
RUN mvn clean package -DskipTests

# --- Stage 2: Run application ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/rms-backend-0.0.1-SNAPSHOT.jar app.jar
COPY --from=build /app/sql ./sql
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
