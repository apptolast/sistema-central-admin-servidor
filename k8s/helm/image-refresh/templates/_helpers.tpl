{{- define "image-refresh.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "image-refresh.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{- define "image-refresh.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "image-refresh.labels" -}}
helm.sh/chart: {{ include "image-refresh.chart" . }}
{{ include "image-refresh.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: apptolast-idp
{{- end }}

{{- define "image-refresh.selectorLabels" -}}
app.kubernetes.io/name: {{ include "image-refresh.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{- define "image-refresh.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "image-refresh.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}
