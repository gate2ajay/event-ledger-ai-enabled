FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Ensure directory for JVM heap dumps exists
RUN mkdir -p /var/log/dumps

# Copy the pre-built bootable jar
COPY build/libs/*.jar app.jar

# Expose port
EXPOSE 8080

# Run with JVM crash dump configuration
ENTRYPOINT ["java", "-XX:+HeapDumpOnOutOfMemoryError", "-XX:HeapDumpPath=/var/log/dumps", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
