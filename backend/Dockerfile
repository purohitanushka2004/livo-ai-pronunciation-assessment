# --- Build stage ---
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

# --- Runtime stage ---
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN apt-get update && \
    apt-get install -y --no-install-recommends ffmpeg curl unzip && \
    rm -rf /var/lib/apt/lists/*

# Free, no-signup Vosk small English model, baked into the image.
RUN mkdir -p /app/models && cd /app/models && \
    curl -LO https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip && \
    unzip vosk-model-small-en-us-0.15.zip && \
    rm vosk-model-small-en-us-0.15.zip

ENV VOSK_MODEL_PATH=/app/models/vosk-model-small-en-us-0.15
ENV PORT=8080

COPY --from=build /app/target/pronunciation-scorer.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
