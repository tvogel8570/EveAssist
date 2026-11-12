# Prod image: the jar that ./mvnw verify built, on a JRE. Build it from App/ after the build, for the instance's CPU:
#   docker buildx build --platform linux/arm64 --build-arg GIT_COMMIT=$(git rev-parse --short HEAD) -t <ecr>/eveassist-app:vX.Y.Z --push .
# There is no RUN step, so building for arm64 on an x86 machine needs no emulation.
FROM eclipse-temurin:25-jre-noble

# the base image's ubuntu user (uid 1000) runs the app, never root
WORKDIR /app
COPY --chown=ubuntu:ubuntu target/app-*.jar app.jar
USER ubuntu

EXPOSE 7444
# the footer shows the commit, so a tester can say which build they saw
ARG GIT_COMMIT=unknown
ENV GIT_COMMIT=${GIT_COMMIT}

# the container's memory limit (compose.yaml) sets the heap; an out-of-memory error ends the process so Compose restarts it
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
