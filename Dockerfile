# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -Dmaven.compiler.fork=false

# Stage 2: Run
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Install curl only (for health checks), clean cache in same layer
RUN apk add --no-cache curl

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-Xmx256m", \
  "-Xms64m", \
  "-XX:+UseSerialGC", \
  "-XX:MaxMetaspaceSize=96m", \
  "-XX:+OptimizeStringConcat", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]