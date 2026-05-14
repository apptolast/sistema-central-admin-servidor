# Docker Hub Latest Refresh

El modo de despliegue automático para `platform` usa imágenes Docker Hub con
tag `latest`, `imagePullPolicy: Always` y un CronJob que reinicia los
Deployments etiquetados cada 5 horas.

Kubernetes solo consulta de nuevo el registry cuando arranca un contenedor.
Por eso el CronJob ejecuta `kubectl rollout restart deployment` sobre los
Deployments con `apptolast.dev/image-refresh=enabled`; al recrearse los Pods,
`imagePullPolicy: Always` resuelve el tag mutable contra Docker Hub.

En desarrollo no se instala este CronJob. Los cambios se despliegan manualmente
con tags concretos o con `helm upgrade --set image.tag=...`.

## Workloads Cubiertos

- `platform`
- `cluster-watcher`
- `rag-query`
- `frontend`

`rag-ingestor` es un CronJob propio. Con `imagePullPolicy: Always` y tag
`latest`, cada ejecución programada ya consulta el registry al crear su Pod.

## Instalacion En Produccion

```bash
helm -n platform upgrade --install image-refresh k8s/helm/image-refresh --wait
```

## Cambiar Workloads A Latest

```bash
helm -n platform upgrade platform k8s/helm/platform --reuse-values \
  --set image.repository=docker.io/apptolast/apptolast-platform \
  --set image.tag=latest \
  --set image.pullPolicy=Always \
  --wait

helm -n platform upgrade cluster-watcher k8s/helm/cluster-watcher --reuse-values \
  --set image.repository=docker.io/apptolast/apptolast-cluster-watcher \
  --set image.tag=latest \
  --set image.pullPolicy=Always \
  --wait

helm -n platform upgrade rag-query k8s/helm/rag-query --reuse-values \
  --set image.repository=docker.io/apptolast/apptolast-rag-query \
  --set image.tag=latest \
  --set image.pullPolicy=Always \
  --wait

helm -n platform upgrade rag-ingestor k8s/helm/rag-ingestor --reuse-values \
  --set image.repository=docker.io/apptolast/apptolast-rag-ingestor \
  --set image.tag=latest \
  --set image.pullPolicy=Always \
  --wait

helm -n platform upgrade frontend k8s/helm/frontend --reuse-values \
  --set image.repository=docker.io/apptolast/apptolast-frontend \
  --set image.tag=latest \
  --set image.pullPolicy=Always \
  --wait
```

## Verificacion

```bash
kubectl -n platform get cronjob image-refresh
kubectl -n platform create job --from=cronjob/image-refresh image-refresh-manual
kubectl -n platform logs job/image-refresh-manual
kubectl -n platform rollout status deploy/platform-apptolast-platform
```

Referencias oficiales:

- Kubernetes Images: `imagePullPolicy: Always` fuerza resolucion de digest al arrancar contenedores: https://kubernetes.io/docs/concepts/containers/images/
- Kubernetes `kubectl rollout restart`: reinicia Deployments por recurso o selector: https://kubernetes.io/docs/reference/kubectl/generated/kubectl_rollout/kubectl_rollout_restart/
- Kubernetes CronJob: el controlador crea Jobs periodicos desde la plantilla configurada: https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/
