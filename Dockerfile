# Stage 1: build jar
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
# Download dependencies trước
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -q

# Stage 2:
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/atm-api.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]