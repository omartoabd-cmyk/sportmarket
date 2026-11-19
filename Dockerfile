# syntax=docker/dockerfile:1
FROM node:24-bookworm-slim AS frontend
WORKDIR /build/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
ENV VITE_API_URL=/api
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build/backend
COPY sportmarket/pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY sportmarket/src ./src
COPY --from=frontend /build/frontend/dist/ ./src/main/resources/static/
RUN mvn -B -ntp -DskipTests package && cp target/*.jar /build/app.jar

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --gid 10001 sportmarket && useradd --uid 10001 --gid sportmarket --no-create-home sportmarket \
    && mkdir -p /app /var/data/uploads && chown -R sportmarket:sportmarket /app /var/data
WORKDIR /app
COPY --from=backend --chown=sportmarket:sportmarket /build/app.jar /app/app.jar
COPY --chmod=755 deploy/entrypoint.sh /app/entrypoint.sh
USER sportmarket
ENV SPRING_PROFILES_ACTIVE=production IMAGE_STORAGE_DIR=/var/data/uploads
EXPOSE 8080
ENTRYPOINT ["/app/entrypoint.sh"]
