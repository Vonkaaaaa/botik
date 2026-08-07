# Multi-stage Docker build for Personal Telegram Bot
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
RUN apk add --no-cache ca-certificates tzdata curl && update-ca-certificates
ENV TZ=Europe/Kyiv
WORKDIR /app
COPY --from=builder /app/target/personal-telegram-bot-1.0.0.jar app.jar

CMD ["java", "-Dfile.encoding=UTF-8", "-jar", "app.jar"]
