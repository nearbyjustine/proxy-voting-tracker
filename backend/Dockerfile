# Runtime image only. The jar is built beforehand (./mvnw package, or the CI build step), which keeps
# the image build fast and lets CI reuse the jar it already tested.
#   ./mvnw -DskipTests package && docker build -t proxyvote-api .
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY target/proxyvote-api-*.jar app.jar
USER app
EXPOSE 8080
# Container-aware heap sizing: use up to 75% of the container's memory limit
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
