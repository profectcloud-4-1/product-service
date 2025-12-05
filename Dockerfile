# ---------------- Builder Stage ----------------
FROM bellsoft/liberica-runtime-container:jdk-17-musl AS builder
WORKDIR /workspace/app

COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./

RUN chmod +x ./gradlew
RUN ./gradlew --no-daemon dependencies || true

COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# ---------------- OTEL Java Agent 다운로드 Stage ----------------
FROM curlimages/curl:8.10.1 AS otel-agent
ARG OTEL_JAVA_AGENT_VERSION=2.9.0

RUN mkdir -p /tmp/otel && \
    curl -L "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_JAVA_AGENT_VERSION}/opentelemetry-javaagent.jar" \
      -o /tmp/otel/opentelemetry-javaagent.jar

# ---------------- Runtime Stage ----------------
FROM bellsoft/liberica-runtime-container:jre-17-musl

WORKDIR /app

# 앱 JAR 복사
COPY --from=builder /workspace/app/build/libs/*.jar /app/app.jar

# OTEL Java Agent 복사
COPY --from=otel-agent /tmp/otel/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar

# 기본 환경 변수
ENV SPRING_PROFILES_ACTIVE=dev,secret
ENV TZ=Asia/Seoul

# OTEL 기본값
ENV OTEL_SERVICE_NAME=product-service
ENV OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
ENV OTEL_EXPORTER_OTLP_PROTOCOL=grpc
EXPOSE 8080

# Java Agent 붙여서 실행
ENTRYPOINT ["sh", "-c", "java \
 -javaagent:/otel/opentelemetry-javaagent.jar \
 -Dotel.service.name=$OTEL_SERVICE_NAME \
 -Dotel.exporter.otlp.endpoint=$OTEL_EXPORTER_OTLP_ENDPOINT \
 -Dotel.exporter.otlp.protocol=$OTEL_EXPORTER_OTLP_PROTOCOL \
 -Duser.timezone=$TZ \
 -jar /app/app.jar"]