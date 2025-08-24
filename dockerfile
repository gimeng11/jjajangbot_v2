# ---- build stage ----
FROM gradle:8.10.2-jdk17 AS builder
WORKDIR /src
COPY --chown=gradle:gradle . .
# 테스트 스킵하고 부트 JAR만 빌드
RUN gradle --no-daemon bootJar -x test \
 && JAR=$(ls build/libs/*.jar | grep -v plain | head -n1) \
 && cp "$JAR" /src/app.jar

# ---- runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app
# 음성 관련 네이티브 의존성 보강 (안전)
RUN apt-get update && apt-get install -y --no-install-recommends libopus0 && rm -rf /var/lib/apt/lists/*
COPY --from=builder /src/app.jar /app/app.jar

# Koyeb는 $PORT로 리스닝해야 함
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
EXPOSE 8080
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
