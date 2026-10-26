# syntax=docker/dockerfile:1.7
# Build a partir da raiz do repositório:  docker build -f payment-service/Dockerfile .

# ---- 1) compila o módulo e as dependências internas (service-chassis) ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY service-chassis service-chassis
COPY order-service order-service
COPY payment-service payment-service
COPY e2e-tests/pom.xml e2e-tests/pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -pl payment-service -am package -DskipTests -Djacoco.skip=true

# ---- 2) separa o jar em camadas (dependências mudam menos que o código) ----
FROM eclipse-temurin:21-jre-alpine AS layers
WORKDIR /layers
COPY --from=build /workspace/payment-service/target/payment-service-*-exec.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ---- 3) imagem final: apenas JRE, usuário sem privilégios ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=layers /layers/extracted/dependencies/ ./
COPY --from=layers /layers/extracted/spring-boot-loader/ ./
COPY --from=layers /layers/extracted/snapshot-dependencies/ ./
COPY --from=layers /layers/extracted/application/ ./
USER app
ENV SERVER_PORT=8082 \
    LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8082
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
    CMD wget -qO- http://localhost:8082/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
