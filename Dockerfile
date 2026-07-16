FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /app

COPY build.gradle settings.gradle ./
COPY gradlew ./
COPY gradle ./gradle/

RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

RUN java -Djarmode=layertools -jar build/libs/*.jar extract

FROM eclipse-temurin:17-jre-jammy AS runner

WORKDIR /app

RUN groupadd -r appgroup && useradd -r -g appgroup appuser

RUN mkdir -p /app/logs && chown -R appuser:appgroup /app

COPY --from=builder --chown=appuser:appgroup /app/dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /app/spring-boot-loader/ ./
COPY --from=builder --chown=appuser:appgroup /app/snapshot-dependencies/ ./

COPY --from=builder --chown=appuser:appgroup /app/application/ ./

USER appuser

EXPOSE 8080

ENV DB_PORT=5432
ENV AWS_REGION=ap-northeast-2
ENV SPRING_PROFILES_ACTIVE=dev

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "org.springframework.boot.loader.launch.JarLauncher"]