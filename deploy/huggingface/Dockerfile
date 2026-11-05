# NexusDesk public browser demo (the "hosted" profile). Runs the released JAR, checked against its
# published SHA-256, as an unprivileged user. Every visitor gets a private in-memory sandbox.
FROM eclipse-temurin:21-jre

ARG NEXUSDESK_VERSION=0.2.1
ARG NEXUSDESK_SHA256=REPLACE_WITH_JAR_SHA256

RUN useradd --create-home --uid 1000 demo
WORKDIR /app
ADD --chown=demo:demo https://github.com/JamieP-205/NexusDesk/releases/download/v${NEXUSDESK_VERSION}/nexusdesk-${NEXUSDESK_VERSION}.jar /app/nexusdesk.jar
RUN echo "${NEXUSDESK_SHA256}  /app/nexusdesk.jar" | sha256sum -c -

USER demo
ENV PORT=7860 \
    NEXUSDESK_HOST=0.0.0.0 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError"
EXPOSE 7860
CMD ["java", "-jar", "/app/nexusdesk.jar", "--spring.profiles.active=hosted"]
