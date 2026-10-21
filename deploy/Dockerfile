# One image for every environment (AD-17). Build from the repository root:
#   docker compose -f deploy/docker-compose.yml build

# --- Stage 1: Angular production build (fails if the generated API client is stale) ---
FROM node:24.20.0-bookworm-slim AS frontend
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# --- Stage 2: Spring Boot jar with the SPA as static resources ---
FROM eclipse-temurin:25.0.4.1_1-jdk-noble AS backend
WORKDIR /src/backend
COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY backend/src/ src/
COPY --from=frontend /src/frontend/dist/frontend/browser/ src/main/resources/static/
RUN ./mvnw -B -q package -DskipTests && cp target/fine-backend-*.jar /tmp/app.jar

# --- Stage 3: runtime ---
FROM eclipse-temurin:25.0.4.1_1-jre-noble
RUN groupadd --system fine && useradd --system --gid fine --home-dir /app fine
ENV APP_HOME=/app
WORKDIR /app
COPY --from=backend /tmp/app.jar /app/app.jar
# Defaults baked into the image; compose mounts the repository copies read-only over them.
COPY config/ /app/config/
COPY data/ /app/data/
COPY models/ /app/models/
USER fine
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
