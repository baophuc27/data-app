# Use the official OpenJDK image as a base
FROM openjdk:17-jdk-alpine

# Set the working directory
WORKDIR /app

# Copy the JAR file into the container
COPY build/libs/transport-0.0.1.jar /app/transport.jar

# Run the application
CMD ["java", "-jar", "transport.jar"]
