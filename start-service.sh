#!/bin/bash

# Load environment variables from .env file
if [ -f .env ]; then
    export $(cat .env | xargs)
fi

ssh -N -f -L localhost:29092:localhost:29092 bas-prod
# Run the Spring Boot application with Gradle
sudo ./gradlew transport:bootRun