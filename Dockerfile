# Build stage
FROM maven:3.9-eclipse-temurin-25-alpine AS build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src src
RUN mvn package -DskipTests -B

# Download dd-java-agent with pinned version and checksum verification
FROM eclipse-temurin:25-jre-alpine AS agent
RUN wget -q -O /dd-java-agent.jar \
    https://github.com/DataDog/dd-trace-java/releases/download/v1.59.0/dd-java-agent-1.59.0.jar \
    && sha256sum /dd-java-agent.jar

# Runtime stage
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -g 1000 app && adduser -u 1000 -G app -D app
RUN apk add --no-cache curl jq bash
RUN mkdir -p /var/log/loterias && chown app:app /var/log/loterias

COPY --from=build /app/target/*.jar app.jar
COPY --from=agent /dd-java-agent.jar dd-java-agent.jar
COPY vault-env.sh .
RUN chmod +x vault-env.sh

USER app

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["/bin/bash", "-c", "source ./vault-env.sh && exec java \
  ${DD_AGENT_ENABLED:+-javaagent:dd-java-agent.jar} \
  ${DD_AGENT_ENABLED:+-Ddd.service=${DD_SERVICE:-loterias}} \
  ${DD_AGENT_ENABLED:+-Ddd.env=${DD_ENV:-prod}} \
  ${DD_AGENT_ENABLED:+-Ddd.version=${DD_VERSION:-0.0.1}} \
  ${DD_AGENT_ENABLED:+-Ddd.logs.injection=true} \
  ${DD_AGENT_ENABLED:+-Ddd.trace.sample.rate=0.5} \
  ${DD_AGENT_ENABLED:+-Ddd.profiling.enabled=true} \
  ${DD_AGENT_ENABLED:+-Ddd.appsec.enabled=true} \
  -jar app.jar --server.port=8081"]
