{{/*
Expand the name of the chart.
*/}}
{{- define "apptolast-platform.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Fully qualified app name.
*/}}
{{- define "apptolast-platform.fullname" -}}
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

{{- define "apptolast-platform.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/* Standard labels. */}}
{{- define "apptolast-platform.labels" -}}
helm.sh/chart: {{ include "apptolast-platform.chart" . }}
{{ include "apptolast-platform.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: apptolast-idp
{{- end }}

{{- define "apptolast-platform.selectorLabels" -}}
app.kubernetes.io/name: {{ include "apptolast-platform.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{- define "apptolast-platform.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "apptolast-platform.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}
