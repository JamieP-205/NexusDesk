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
# Unpack the jar into the application jar plus its libraries. Unlike the single nested jar, this
# layout can be recorded in a class-data-sharing archive (below).
RUN java -Djarmode=tools -jar /nexusdesk.jar extract --destination /extracted \
    && mv /extracted/nexusdesk-*.jar /extracted/nexusdesk.jar

FROM eclipse-temurin:21-jre
# The base image already has a user with ID 1000 (ubuntu), so the demo user gets its own.
RUN useradd --create-home --uid 10001 demo && mkdir /app && chown demo:demo /app
COPY --from=build --chown=demo:demo /extracted /app
USER demo
# Heap and native memory sized to stay inside 512 MB. A local run with 60 visitors open used
# about 60 MB of heap and 220 MB in total. Render sets PORT.
ENV NEXUSDESK_HOST=0.0.0.0 \
    JAVA_TOOL_OPTIONS="-Xmx240m -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=48m -XX:+UseSerialGC -Xss512k -XX:+ExitOnOutOfMemoryError"
# Class-data-sharing archive. One training run at build time records the classes the app loads
# (Spring, Tomcat, Flyway, H2), so a cold start maps them instead of reading, parsing and verifying
# them again, which is most of the start-up time on a small CPU share. The training run exits as soon
# as the context has refreshed. If the archive were missing or stale, Java would ignore it and
# start normally.
RUN java -XX:ArchiveClassesAtExit=/app/app.jsa -Dspring.context.exit=onRefresh \
    -jar /app/nexusdesk.jar --spring.profiles.active=hosted
CMD ["java", "-XX:SharedArchiveFile=/app/app.jsa", "-jar", "/app/nexusdesk.jar", "--spring.profiles.active=hosted"]
