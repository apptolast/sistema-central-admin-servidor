FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --home-dir /app app

COPY build/libs/cluster-watcher.jar /app/app.jar

RUN chown -R app:app /app

USER app

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
