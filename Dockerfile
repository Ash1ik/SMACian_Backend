# ======================================================
# SMACian Backend - Dockerfile
# Uses a multi-stage build: one image to compile, one to run.
# This makes the final image small and secure.
# ======================================================

# Stage 1: BUILD
# gradle:8.10.2-jdk17 already has both Gradle and a JDK pre-installed.
FROM gradle:8.10.2-jdk17 AS build

# Run the build as root so Gradle can write anywhere and the wrapper
# executes regardless of the image's default user.
USER root
WORKDIR /home/app

# Copy only the build files first (Gradle caches dependencies per layer,
# so this step is cached unless build.gradle changes).
COPY build.gradle settings.gradle ./
COPY gradlew gradlew.bat ./
COPY gradle ./gradle

# Make sure the Gradle wrapper is executable, then pre-download
# all dependencies (build starts much faster later).
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon --console=plain > /dev/null 2>&1 || true

# Copy the actual source code.
COPY src ./src

# Compile the project and create the executable JAR.
RUN ./gradlew clean bootJar --no-daemon --console=plain -q

# Stage 2: RUN
# eclipse-temurin is the official OpenJDK image. jre = runtime only, no compiler.
FROM eclipse-temurin:17-jre AS run

# Create a non-root user for security (containers shouldn't run as root).
RUN useradd --create-home appuser
USER appuser
WORKDIR /app

# Copy the built JAR from the build stage.
COPY --from=build /home/app/build/libs/*.jar app.jar

# Render injects PORT and routes requests to the container's exposed port.
EXPOSE 8080

# Start the Spring Boot application.
ENTRYPOINT ["java", "-jar", "app.jar"]