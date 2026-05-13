# syntax=docker/dockerfile:1.7
# ============================================================================
# frontend — Compose Multiplatform Web (Wasm) servido por nginx.
# Multi-stage: stage 1 compila Wasm con Gradle, stage 2 sirve estático.
# ============================================================================

# ── Stage 1: build Wasm distribution ────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy gradle wrapper desde el monolito (asume build context = ./frontend con
# acceso a ../platform/gradle vía Docker buildx context). Simplificación:
# duplicar el wrapper en frontend/gradle/ con un Make target. Phase 5 cont.
#
# Por ahora usamos el system gradle de la imagen base con un fallback a
# descargar wrapper si NO existe.
COPY . /workspace
RUN if [ ! -x ./gradlew ]; then \
        apt-get update && apt-get install -y --no-install-recommends curl unzip && \
        curl -sSL https://services.gradle.org/distributions/gradle-8.14.3-bin.zip -o /tmp/g.zip && \
        unzip -q /tmp/g.zip -d /opt && \
        ln -s /opt/gradle-8.14.3/bin/gradle /usr/local/bin/gradle ; \
    fi

# Build production Wasm distribution.
RUN gradle --no-daemon :composeApp:wasmJsBrowserDistribution

# ── Stage 2: nginx servidor estático ────────────────────────────────────────
FROM nginx:1.27-alpine

# Copy production Wasm bundle.
COPY --from=build /workspace/composeApp/build/dist/wasmJs/productionExecutable/ /usr/share/nginx/html/

# Nginx config con SPA fallback + gzip + cache headers.
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Defensa básica: no run as root, sin cap_net_bind extra (puerto > 1024).
# El base image nginx:alpine ya configura nginx user; sólo cambiamos a un
# puerto no privilegiado y los logs a stdout/stderr.
RUN sed -i 's|listen       80;|listen       8080;|' /etc/nginx/conf.d/default.conf || true && \
    chown -R nginx:nginx /usr/share/nginx/html /var/cache/nginx

USER nginx
EXPOSE 8080

# Healthcheck simple: nginx responde al / con 200 o 304.
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
