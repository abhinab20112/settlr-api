# Stage 1: Build the application
FROM maven:3.9.6-eclipse-temurin-21-jammy AS builder
WORKDIR /app

# Copy the pom.xml and download dependencies
# This step is cached as long as the pom.xml doesn't change
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy the source code and build the application
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Copy the built jar from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Expose the default port (optional, but good practice)
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-Xmx256m", "-jar", "app.jar"]
