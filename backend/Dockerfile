# Build with the Maven wrapper, run on a JRE only.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn ./.mvn
# Dependencies in their own layer, so code changes don't download them again.
RUN ./mvnw -B -q dependency:go-offline
COPY src ./src
RUN ./mvnw -B -q package -DskipTests && cp target/*.jar /app.jar

FROM eclipse-temurin:21-jre
RUN useradd --create-home --uid 10001 medfold \
    && mkdir -p /data/uploads && chown medfold /data/uploads
USER medfold
WORKDIR /app
COPY --from=build /app.jar app.jar

# Uploaded files live on a volume mounted here (see docker-compose.yml).
ENV STORAGE_ROOT=/data/uploads
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
