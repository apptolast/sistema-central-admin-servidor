---
title: "cert-manager — limpieza de certs huérfanos (Prometheus/Grafana/Alertmanager y otros)"
type: runbook
owner: pablo
source-of-truth: "kubectl get certificates -A"
last-verified: 2026-05-13
tags: [cert-manager, cleanup, P1]
severity: P1
trigger:
  - manual: revisión periódica (mensual) o tras detectar logs ruidosos en cert-manager
audience: [oncall, sysadmin]
applies-to:
  cluster: kubeadm v1.32.3
  cert-manager: v1.17.2
  issuer: cloudflare-clusterissuer (DNS01)
---

# RB — cert-manager orphan cleanup

## 1 Síntoma

`kubectl get certificates -A` lista certificados emitidos para hosts cuyo `IngressRoute` ya no existe o cuyo backend nunca se desplegó. Síntomas:

- Logs ruidosos en `cert-manager` (renovaciones cada 60d sin servir tráfico).
- Cloudflare DNS01 burst de challenges innecesarios.
- Riesgo de hit rate-limit de Let's Encrypt si se hace bulk renovation.

Huérfanos confirmados al 2026-05-12:

| Namespace | Certificate | Por qué huérfano |
|-----------|-------------|------------------|
| monitoring | alertmanager-dot-apptolast-com-tls | Sin pod Alertmanager desplegado |
| monitoring | grafana-dot-apptolast-com-tls | Sin pod Grafana |
| monitoring | prometheus-dot-apptolast-com-tls | Sin pod Prometheus |
| monitoring | logs-tls | Sin backend (Loki/dozzle usa dozzle-tls) |
| langflow | generator-ui-tls | Microservicio no desplegado |
| langflow | llm-router-tls | Microservicio no desplegado |
| langflow | rag-service-tls | Microservicio no desplegado |
| redisinsight | kali-apptolast-com-tls | Hostname `kali.apptolast.com` no enrutado |

[source: cluster-ops/audit/REPORT_2026-05-05_DELTA.md#cert-manager@HEAD]

## 2 Pre-checks

### 2.1 Confirmar huérfano por cada cert candidato

Para cada candidato:

```bash
NS=monitoring
CERT=alertmanager-dot-apptolast-com-tls

# Hostname que cubre
HOST=$(kubectl -n "$NS" get certificate "$CERT" -o jsonpath='{.spec.dnsNames[0]}')
echo "Cert $NS/$CERT cubre $HOST"

# ¿Algún IngressRoute usa el secret de este cert?
SECRET=$(kubectl -n "$NS" get certificate "$CERT" -o jsonpath='{.spec.secretName}')
kubectl get ingressroutes.traefik.io -A -o json \
  | jq --arg ns "$NS" --arg secret "$SECRET" \
       '[.items[] | select(.spec.tls.secretName == $secret or (.spec.tls.secretName // "") == $secret)]'

# ¿Algún Ingress clásico?
kubectl get ingress -A -o json \
  | jq --arg host "$HOST" '[.items[] | select(.spec.tls // [] | map(.hosts[]?) | any(. == $host))]'

# ¿Hay DNS A record apuntando aquí? (info-only — Cloudflare es source-of-truth)
dig +short "$HOST"
```

Decisión:
- Si los 3 queries devuelven `[]` y el host **no** está en uso → eliminar.
- Si algún query devuelve match → NO eliminar, hay tráfico esperándolo.

## 3 Eliminación

Para cada cert confirmado huérfano:

```bash
NS=monitoring
CERT=alertmanager-dot-apptolast-com-tls
SECRET=$(kubectl -n "$NS" get certificate "$CERT" -o jsonpath='{.spec.secretName}')

# 1. Borrar el Certificate (cert-manager dejará de renovar)
kubectl -n "$NS" delete certificate "$CERT"

# 2. Borrar el Secret asociado
kubectl -n "$NS" delete secret "$SECRET" --ignore-not-found

# 3. Decidir si borrar el DNS A record en Cloudflare
# Si el subdominio no se va a usar nunca más, borrar manualmente desde el panel
# Cloudflare → DNS → records → eliminar el record A correspondiente.
# Si está pensado para uso futuro: dejar el DNS, eliminar sólo el cert.
```

Repetir para cada candidato del paso 1.

## 4 Limpieza en lote (opcional — sólo cuando lista validada)

```bash
cat > /tmp/orphan-certs.txt <<EOF
monitoring/alertmanager-dot-apptolast-com-tls
monitoring/grafana-dot-apptolast-com-tls
monitoring/prometheus-dot-apptolast-com-tls
monitoring/logs-tls
langflow/generator-ui-tls
langflow/llm-router-tls
langflow/rag-service-tls
redisinsight/kali-apptolast-com-tls
EOF

while IFS=/ read -r ns cert; do
  secret=$(kubectl -n "$ns" get certificate "$cert" -o jsonpath='{.spec.secretName}' 2>/dev/null) || continue
  echo "Deleting $ns/$cert (secret=$secret)"
  kubectl -n "$ns" delete certificate "$cert" --ignore-not-found
  kubectl -n "$ns" delete secret "$secret" --ignore-not-found
done < /tmp/orphan-certs.txt
```

## 5 Verificación post

```bash
kubectl get certificates -A | wc -l    # debe bajar en N (número de eliminados)
kubectl -n cert-manager logs deploy/cert-manager --tail 50 | grep -iE 'error|fail'
# Esperar 24h y revisar de nuevo: no debe haber renovation attempts fallidos para los hosts eliminados.
```

## 6 Rollback

cert-manager regenera el cert automáticamente si vuelves a crear el `Certificate` resource. No hay rollback destructivo. Si te equivocaste:

```bash
# El YAML original suele estar en git (k8s-manifests/) o en el helm chart que lo creó.
# Aplicar de nuevo:
kubectl apply -f k8s-manifests/<original-cert>.yaml
```

Cloudflare DNS01 emitirá un nuevo cert en 2-5 min.

## 7 Política de prevención

Para que esto no se repita:

1. **Antes de borrar un IngressRoute**, borrar primero su `Certificate` asociado.
2. **Si despliegas un cert "para uso futuro"**, etiqueta:
   ```yaml
   metadata:
     labels:
       apptolast.com/expected-backend: "reserved"
   ```
3. **Cronjob `cert-checks`** en cluster-ops verifica expiración pero no detecta huérfanos. Considerar añadir un check de uso (ver `cluster-ops/audit/RUNBOOKS/RB-18_CERT_EXPIRY.md`).

## 8 Citación

- Inventario cert-manager 2026-05-12: 40+ certificates [source: cluster-ops/audit/baseline/certs.txt@HEAD]
- ClusterIssuer cloudflare-clusterissuer Ready 360d [source: dossier 2026-05-12 §5 Networking@HEAD]
- IngressRoutes activos: 35 [source: dossier 2026-05-12 §5 Traefik IngressRoutes@HEAD]
