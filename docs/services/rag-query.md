---
title: "Service: rag-query"
type: service
owner: pablo
last-verified: 2026-05-14
source-of-truth: services/rag-query/
audience: [backend-dev, frontend-dev, devops, oncall]
applies-to-phase: 3 (Knowledge/RAG)
---

# rag-query

Microservicio HTTP que consulta la base vectorial de conocimiento y devuelve
evidencia documentada para la interfaz Ask del IDP.

## Contrato HTTP

Endpoint:

```http
POST /api/v1/rag/query
Content-Type: application/json
```

Request:

```json
{
  "question": "Como se despliega rag-ingestor?",
  "topK": 5
}
```

Response con evidencia suficiente:

```json
{
  "question": "Como se despliega rag-ingestor?",
  "confidence": "HIGH",
  "status": "CITED",
  "body": "contenido recuperado de chunks documentados",
  "chunks": [],
  "citations": [],
  "warning": null
}
```

Response sin evidencia suficiente:

```json
{
  "confidence": "LOW_NO_EVIDENCE",
  "status": "LOW_NO_EVIDENCE",
  "body": null,
  "chunks": [],
  "citations": [],
  "warning": "No encuentro evidencia documentada suficiente. Revisa runbooks o pide ayuda humana antes de actuar."
}
```

## Regla anti-alucinacion

`body` solo se construye a partir de `chunks.content` ya recuperados. Si la
confianza es `LOW_NO_EVIDENCE`, el servicio no sintetiza respuesta y deja
`body = null`.

## Verificacion

```bash
curl -sk https://idp.apptolast.com/api/v1/rag/query \
  -H 'content-type: application/json' \
  --data '{"question":"Como se despliega rag-ingestor?","topK":5}' | jq
```

Comprobar:

- `status` debe ser `CITED` cuando hay evidencia.
- `body` debe venir informado.
- `citations` debe contener las fuentes recuperadas.

## Despliegue

```bash
cd services/rag-query
../../platform/gradlew build --no-daemon

docker build -t docker.io/apptolast/apptolast-rag-query:<tag> .
docker push docker.io/apptolast/apptolast-rag-query:<tag>

cd ../..
helm -n platform upgrade rag-query k8s/helm/rag-query --reuse-values \
  --set image.repository=docker.io/apptolast/apptolast-rag-query \
  --set image.tag=<tag> \
  --set image.pullPolicy=Always \
  --wait
```
