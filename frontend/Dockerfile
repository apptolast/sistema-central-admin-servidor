# syntax=docker/dockerfile:1.7
# ============================================================================
# frontend — nginx-only stage. La compilación Wasm ocurre en el job
# frontend-build de CI y produce un artifact `frontend-dist/` que docker-frontend
# descarga a ./dist/ antes del docker build.
#
# Para dev local: el operador genera la dist con
#   cd frontend && ./gradlew :composeApp:wasmJsBrowserDistribution
# y copia composeApp/build/dist/wasmJs/productionExecutable/[recurso] a frontend/dist/
# antes de `docker build .`.
# ============================================================================

FROM nginx:1.27-alpine

# Copy production Wasm bundle from the pre-built artifact.
COPY dist/ /usr/share/nginx/html/

# Nginx config con SPA fallback + gzip + cache headers.
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Configurar nginx para correr non-root:
#  - listen en 8080 (no privilege port)
#  - pid file en /tmp/nginx.pid (writable por nginx user)
#  - chown de dirs que nginx necesita escribir (cache, log, run)
RUN sed -i 's|listen       80;|listen       8080;|' /etc/nginx/conf.d/default.conf && \
    sed -i 's|pid        /run/nginx.pid;|pid        /tmp/nginx.pid;|' /etc/nginx/nginx.conf && \
    chown -R nginx:nginx /usr/share/nginx/html /var/cache/nginx /var/log/nginx && \
    chmod -R g+w /var/cache/nginx /var/log/nginx && \
    touch /tmp/nginx.pid && chown nginx:nginx /tmp/nginx.pid

USER nginx
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
