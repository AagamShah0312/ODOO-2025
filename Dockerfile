# --- Build stage -------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY src ./src
RUN mkdir -p out \
    && find src/main/java -name "*.java" > /tmp/sources.txt \
    && javac -encoding UTF-8 -d out @/tmp/sources.txt

# --- Runtime stage ------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Pull in the latest Alpine security patches so the image stays up to date
RUN apk upgrade --no-cache \
    && adduser -D -u 1000 app

COPY --from=build /app/out /app/out
COPY src/main/resources/static /app/static

ENV SKILLSWAP_STATIC=/app/static
ENV PORT=8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Djava.awt.headless=true"

EXPOSE 8080
USER app

HEALTHCHECK --interval=20s --timeout=5s --start-period=15s --retries=5 \
    CMD wget -qO- "http://127.0.0.1:${PORT}/api/health" || exit 1

CMD ["sh", "-c", "exec java $JAVA_OPTS -cp out com.skillswap.SkillSwapApp"]
