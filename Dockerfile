FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY src ./src
RUN mkdir -p out \
    && find src -name "*.java" > /tmp/sources.txt \
    && javac -encoding UTF-8 -d out @/tmp/sources.txt

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN adduser -D -u 1000 app
COPY --from=build /app/out /app/out
COPY src/main/resources/static /app/static
ENV SKILLSWAP_STATIC=/app/static
ENV PORT=8080
EXPOSE 8080
USER app
CMD ["java", "-cp", "out", "com.skillswap.SkillSwapApp"]
