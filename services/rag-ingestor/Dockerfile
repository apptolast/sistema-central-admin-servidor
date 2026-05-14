FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN apt-get update && \
    apt-get install -y --no-install-recommends ca-certificates git && \
    rm -rf /var/lib/apt/lists/*

RUN groupadd --system app && useradd --system --gid app --home-dir /app app

COPY build/libs/rag-ingestor.jar /app/app.jar

RUN chown -R app:app /app

USER app

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
