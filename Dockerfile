# Stage 1: build and test. It runs in Cloud Build, so neither Maven nor Docker is needed on the developer machine.
# Base images are pinned by tag and by digest (looked up on Docker Hub), so a rebuild cannot silently pick up a different image.
FROM maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B package

# Stage 2: run. Tomcat 10.1 (Servlet 6) with the war as the root application, as a non-root user.
FROM tomcat:10.1.60-jre21-temurin-noble@sha256:890e010ceb328cd98601210047cf0a1ad00bee6096ee0965d96d383d9ebea4a7
RUN rm -rf /usr/local/tomcat/webapps/* \
    && groupadd --system --gid 10001 yurune \
    && useradd --system --uid 10001 --gid yurune --no-create-home --shell /usr/sbin/nologin yurune
COPY docker/server.xml /usr/local/tomcat/conf/server.xml
COPY --from=build /app/target/yurune.war /usr/local/tomcat/webapps/ROOT.war
# Only the folders Tomcat writes to belong to the app user. The configuration stays read-only (owned by root).
RUN chown -R yurune:yurune /usr/local/tomcat/webapps /usr/local/tomcat/work /usr/local/tomcat/temp /usr/local/tomcat/logs
USER yurune

# Cloud Run sends traffic to $PORT (8080 by default); server.xml reads it from the http.port property.
ENV CATALINA_OPTS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"
EXPOSE 8080
CMD ["sh", "-c", "exec env CATALINA_OPTS=\"$CATALINA_OPTS -Dhttp.port=${PORT:-8080}\" catalina.sh run"]
