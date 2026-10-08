# ---------- build stage ----------
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src/ src/
RUN ./mvnw -q -B clean package -DskipTests && \
    java -Djarmode=tools -jar target/fleet-operations-backend.jar extract --layers --destination extracted

# ---------- runtime stage ----------
FROM eclipse-temurin:25-jre-alpine AS runtime
LABEL org.opencontainers.image.title="LIMOZ Fleet Operations Backend" \
      org.opencontainers.image.source="https://github.com/limoz-rwanda/fleet-operations-backend"
RUN addgroup -S fleet && adduser -S fleet -G fleet && \
    apk add --no-cache curl tzdata && \
    mkdir -p /app/data/uploads && chown -R fleet:fleet /app
WORKDIR /app
ENV TZ=Africa/Kigali \
    SPRING_PROFILES_ACTIVE=prod \
    STORAGE_LOCAL_PATH=/app/data/uploads \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"
# Layered copy: dependencies change rarely, application code changes often -> small rebuilds
COPY --from=build --chown=fleet:fleet /workspace/extracted/dependencies/ ./
COPY --from=build --chown=fleet:fleet /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=fleet:fleet /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=fleet:fleet /workspace/extracted/application/ ./
USER fleet
EXPOSE 8080
VOLUME ["/app/data"]
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
  CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar fleet-operations-backend.jar"]
