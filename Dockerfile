FROM node:22-alpine AS web
WORKDIR /web
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./pom.xml
COPY modules/ ./modules/
COPY backend/ ./backend/
COPY --from=web /web/dist/ ./backend/src/main/resources/static/
RUN mvn -B -ntp -pl backend -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S vault && adduser -S -G vault vault
USER vault
WORKDIR /app
COPY --from=build /build/backend/target/media-vault-0.1.0.jar /app/app.jar
EXPOSE 8080
ENV SERVER_ADDRESS=0.0.0.0
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]
