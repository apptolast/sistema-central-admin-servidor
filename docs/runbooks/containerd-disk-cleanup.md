---
title: "Containerd disk cleanup — /var/lib/containerd al 48 GiB"
type: runbook
owner: pablo
source-of-truth: "df -h / && sudo du -sh /var/lib/containerd"
last-verified: 2026-05-13
tags: [disk, containerd, host, P1]
severity: P1
trigger:
  - alert: HostDiskHigh (raíz > 80%)
  - manual: cuando FreeDiskSpaceFailed aparece en eventos
audience: [oncall, sysadmin]
applies-to:
  host: AppToLastServer
  cluster: kubeadm v1.32.3 single-node
---

# RB — Containerd disk cleanup

## 1 Síntoma

- `df -h /` muestra uso > 70% en `/dev/sda1`.
- `kubectl get events -A --field-selector type=Warning` muestra repetido:
  ```
  FreeDiskSpaceFailed: Failed to garbage collect required amount of images.
  Attempted to free 30688637747 bytes, but only found 0 bytes eligible to free.
  ```
- Causa real (verificada 2026-05-12): 48 GiB en `/var/lib/containerd` con **todas** las imágenes en uso → 0 candidatos para GC.

[source: cluster-ops/audit/REPORT_2026-05-03.md#disk@HEAD]

## 2 Pre-checks (sin tocar nada)

```bash
df -h /
sudo du -sh /var/lib/containerd /var/lib/kubelet /home /var/log
sudo crictl --runtime-endpoint unix:///run/containerd/containerd.sock images | wc -l
kubectl get events -A --field-selector type=Warning --sort-by=.lastTimestamp | tail
```

**Decision point:**
- Uso entre 70 % y 80 %: opción A (cleanup conservador).
- Uso > 80 %: opción B (mover containerd al HC Volume — requiere ventana de mantenimiento).
- Uso > 90 %: opción C (resize Hetzner Volume +50 GB — primero, después containerd).

## 3 Opción A — Cleanup conservador (no interrumpe servicio)

### A.1 Borrar imágenes containerd no usadas

```bash
# Listar imágenes y referencias actuales
sudo crictl --runtime-endpoint unix:///run/containerd/containerd.sock images
sudo crictl --runtime-endpoint unix:///run/containerd/containerd.sock rmi --prune
```

`rmi --prune` borra sólo las que no están referenciadas por ningún container vivo. Idempotente.

### A.2 Rotar logs antiguos

```bash
sudo journalctl --vacuum-time=14d
sudo find /var/log -type f -name "*.gz" -mtime +30 -delete
```

### A.3 Limpiar /home cacheados (cuidado)

```bash
du -sh /home/admin/.gradle /home/admin/.npm /home/admin/.cache /home/admin/n8n-backup
# Decisión humana: borrar manualmente lo que no se necesite. NO usar rm -rf en /home/admin/.
```

### A.4 Verificación post

```bash
df -h /
kubectl get events -A --field-selector type=Warning --sort-by=.lastTimestamp | tail
```

Si `FreeDiskSpaceFailed` desaparece dentro de 15 minutos → done.

## 4 Opción B — Mover containerd al HC Volume (ventana mantenimiento)

> **Impacto**: todos los pods se reinician. Ventana ≈ 10-20 min. Programar fuera de pico.

### B.1 Pre-flight

```bash
df -h /mnt/HC_Volume_103965858    # debe quedar > 30 GB libres después
sudo systemctl status containerd  # confirmar activo
ls /mnt/HC_Volume_103965858/      # mostrará longhorn/, backups/, rancher-backups/
```

### B.2 Drain (en single-node es cordon + delete pods)

```bash
kubectl cordon apptolastserver
# NO usar `kubectl drain` en single-node — quedaría todo unscheduled. En su lugar:
# Esperar a que cluster-ops `cluster-self-healing` esté inactivo y aplicar el cambio.
```

### B.3 Mover containerd data-root

```bash
sudo systemctl stop kubelet
sudo systemctl stop containerd

sudo mkdir -p /mnt/HC_Volume_103965858/containerd
sudo rsync -aHAXxv --numeric-ids --info=progress2 /var/lib/containerd/ /mnt/HC_Volume_103965858/containerd/

# Editar config — añadir/modificar root:
sudo cp /etc/containerd/config.toml /etc/containerd/config.toml.bak.$(date +%s)
sudo sed -i 's|^root = "/var/lib/containerd"|root = "/mnt/HC_Volume_103965858/containerd"|' /etc/containerd/config.toml
# Si la línea no existe, añadirla al inicio del [plugins...] block. Ver containerd docs.

sudo mv /var/lib/containerd /var/lib/containerd.old
sudo ln -s /mnt/HC_Volume_103965858/containerd /var/lib/containerd

sudo systemctl start containerd
sudo systemctl start kubelet
```

### B.4 Verificación

```bash
sudo crictl --runtime-endpoint unix:///run/containerd/containerd.sock info | grep -i root
kubectl get pods -A | grep -vE 'Running|Completed'    # debería estar vacío en ~5 min
df -h /
```

### B.5 Cleanup (sólo cuando todo está estable >30 min)

```bash
sudo uncordon apptolastserver   # ya estaba uncordoned en single-node, no-op
sudo rm -rf /var/lib/containerd.old
```

## 5 Opción C — Resize Hetzner Volume

NO documentado aquí porque depende del panel Hetzner. Ver `cluster-ops/audit/HERMES_VS_OPENCLAW_DECISION.md` para política de redimensionado.

## 6 Rollback

Si tras Opción B el cluster no arranca:

```bash
sudo systemctl stop kubelet containerd
sudo rm /var/lib/containerd                                   # quitar symlink
sudo mv /var/lib/containerd.old /var/lib/containerd           # restaurar
sudo cp /etc/containerd/config.toml.bak.* /etc/containerd/config.toml
sudo systemctl start containerd kubelet
```

## 7 Citación

- Estado del disco verificado 2026-05-12 21:10 UTC: 105G/150G = 73% [source: cluster-ops/audit/baseline/disk_host.txt@HEAD]
- HC Volume tiene 115G libres de 196G [source: cluster-ops/audit/REPORT_2026-05-03.md#disk@HEAD]
- containerd v2.2.2 → soporta `root` config en `/etc/containerd/config.toml` [source: https://github.com/containerd/containerd/blob/main/docs/config.md]
