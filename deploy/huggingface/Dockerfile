# NexusDesk public browser demo (the "hosted" profile). Builds the app from a pinned commit of the
# public repository, then runs it as an unprivileged user. Every visitor gets a private in-memory sandbox.
FROM eclipse-temurin:21-jdk AS build
ARG NEXUSDESK_COMMIT=main
RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/*
WORKDIR /src
RUN git clone https://github.com/JamieP-205/NexusDesk.git . && git checkout "$NEXUSDESK_COMMIT" \
 && ./mvnw -B -q package -DskipTests && cp target/nexusdesk-*.jar /nexusdesk.jar

FROM eclipse-temurin:21-jre
RUN useradd --create-home --uid 1000 demo
COPY --from=build --chown=demo:demo /nexusdesk.jar /app/nexusdesk.jar
USER demo
ENV PORT=7860 \
    NEXUSDESK_HOST=0.0.0.0 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError"
EXPOSE 7860
CMD ["java", "-jar", "/app/nexusdesk.jar", "--spring.profiles.active=hosted"]
