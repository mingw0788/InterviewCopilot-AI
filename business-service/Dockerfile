FROM maven:3.9.16-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml ./
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress package -DskipTests

FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl \
    && addgroup -S app \
    && adduser -S app -G app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/interviewcopilot-business-service-*.jar ./application.jar

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
