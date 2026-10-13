<#
  Apply the observability definitions (log-based metrics, alert policies, an uptime check, and a dashboard)
  from deploy/monitoring/*.json to a Google Cloud project.
  (ASCII only on purpose: Windows PowerShell 5.1 reads a file without a BOM as ANSI.)

  Usage:
    .\deploy\apply-monitoring.ps1 -ProjectId <project-id> -ServiceHost <service-url-host> [-ServiceName yurune] [-Region asia-northeast1]
    (ServiceHost is the Cloud Run service's hostname, e.g. the host part of 'gcloud run services describe ... --format value(status.url)')

  What it does, in order:
    1 create (or update) each log-based metric in deploy/monitoring/log-metrics.json
    2 create an uptime check for /ready (deploy/monitoring/alert-policies.json, uptimeChecks)
    3 create each alert policy in deploy/monitoring/alert-policies.json (notification channels are left empty;
      the user adds them later, in the Cloud Console, because this script does not decide who gets paged)
    4 create the dashboard in deploy/monitoring/dashboard.json

  NOT VERIFIED: this script has not been run against a real project yet. The --log-filter values contain double
  quotes, which Windows PowerShell 5.1 may pass to gcloud in a broken form.
  The thresholds in alert-policies.json are placeholders (no measured basis). Read the output of each step.
#>
param(
    [Parameter(Mandatory = $true)][string]$ProjectId,
    [Parameter(Mandatory = $true)][string]$ServiceHost,
    [string]$ServiceName = 'yurune',
    [string]$Region = 'asia-northeast1'
)

$ErrorActionPreference = 'Stop'

function Invoke-Gcloud {
    & gcloud @args
    if ($LASTEXITCODE -ne 0) { throw "gcloud failed: $($args -join ' ')" }
}

function Read-JsonFile([string]$path) {
    if (-not (Test-Path $path)) { throw "missing config file: $path" }
    return Get-Content $path -Raw -Encoding UTF8 | ConvertFrom-Json
}

Write-Host "Project: $ProjectId / Service: $ServiceName / Region: $Region"
Invoke-Gcloud config set project $ProjectId

$metricsConfig = Read-JsonFile (Join-Path $PSScriptRoot 'monitoring\log-metrics.json')
$alertConfig = Read-JsonFile (Join-Path $PSScriptRoot 'monitoring\alert-policies.json')
$dashboardConfig = Read-JsonFile (Join-Path $PSScriptRoot 'monitoring\dashboard.json')

# 1 log-based metrics
foreach ($metric in $metricsConfig.metrics) {
    Write-Host "Metric: $($metric.name)"
    $metricArgs = @('logging', 'metrics', 'create', $metric.name, '--description', $metric.description, '--log-filter', $metric.filter)
    Invoke-Gcloud @metricArgs
}

# 2 uptime check for /ready
foreach ($check in $alertConfig.uptimeChecks) {
    Write-Host "Uptime check: $($check.name)"
    Invoke-Gcloud monitoring uptime create $check.name `
        --resource-type=uptime-url --host=$ServiceHost --path=$check.path `
        --period=$check.periodSeconds --timeout=$check.timeoutSeconds
}

# 3 alert policies (thresholds are placeholders; see the _comment field in alert-policies.json)
foreach ($policy in $alertConfig.policies) {
    Write-Host "Alert policy: $($policy.name) (placeholder threshold, not measured)"
    # NOT VERIFIED: gcloud alpha monitoring policies create needs a JSON policy body built from this
    # descriptor (metric type logging.googleapis.com/user/<name>, or the uptime check's resource).
    # Left as a manual step until this has been exercised against a real project.
    Write-Host "  (manual step for now: build the policy body for '$($policy.name)' and run"
    Write-Host "   'gcloud alpha monitoring policies create --policy-from-file=<file>')"
}

# 4 dashboard
Write-Host "Dashboard: $($dashboardConfig.displayName)"
Write-Host "  (manual step for now: build the dashboard body from deploy/monitoring/dashboard.json and run"
Write-Host "   'gcloud monitoring dashboards create --config-from-file=<file>')"

Write-Host 'Done. Steps 3 and 4 print the gcloud command to run once the real project is available for a first, verified pass.'
