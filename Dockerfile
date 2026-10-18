# Stage 1: Build the application
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Install Maven in the Alpine container
RUN apk add --no-cache maven

# Copy project files
COPY . .

# Build using standard maven command
RUN mvn clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]