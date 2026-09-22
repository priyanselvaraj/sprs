# ==========================================
# STAGE 1: Build JAR using Maven
# ==========================================
FROM maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /app

# Copy backend pom.xml and source from monorepo
COPY backend/pom.xml .
COPY backend/src ./src

# Build production package
RUN mvn clean package -DskipTests

# ==========================================
# STAGE 2: Lightweight Production Runtime
# ==========================================
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Install curl
RUN apk add --no-cache curl

# Create non-root system user for secure container execution
RUN addgroup -S sprgroup && adduser -S spruser -G sprgroup

# Copy generated JAR from builder stage
COPY --from=builder /app/target/sprsystem-*.jar app.jar

# Set container ownership to non-root user
RUN chown -R spruser:sprgroup /app

USER spruser

# Expose Spring Boot default application port
EXPOSE 8080

# Environment variable defaults
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-Xms128m -Xmx320m -Xss512k -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC -XX:+TieredCompilation -XX:TieredStopAtLevel=1 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT:-8080} -Dserver.address=0.0.0.0 -jar app.jar"]
