# NexusDesk public browser demo (the "hosted" profile) on a Render free web service: 512 MB of memory
# and a small share of a CPU. Built from this repository, run as an unprivileged user.
# Every visitor gets a private in-memory sandbox; nothing is written to disk.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests && cp target/nexusdesk-*.jar /nexusdesk.jar

FROM eclipse-temurin:21-jre
RUN useradd --create-home --uid 1000 demo
COPY --from=build --chown=demo:demo /nexusdesk.jar /app/nexusdesk.jar
USER demo
# Heap and native memory sized to stay inside 512 MB. A local run with 60 visitors open used
# about 60 MB of heap and 220 MB in total. Render sets PORT.
ENV NEXUSDESK_HOST=0.0.0.0 \
    JAVA_TOOL_OPTIONS="-Xmx240m -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=48m -XX:+UseSerialGC -Xss512k -XX:+ExitOnOutOfMemoryError"
CMD ["java", "-jar", "/app/nexusdesk.jar", "--spring.profiles.active=hosted"]
