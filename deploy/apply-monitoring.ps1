<#
  Apply the observability definitions (log-based metrics, an uptime check, alert policies, and a dashboard)
  from deploy/monitoring/*.json to a Google Cloud project.
  (ASCII only on purpose: Windows PowerShell 5.1 reads a file without a BOM as ANSI.)

  Usage:
    gcloud auth login
    .\deploy\apply-monitoring.ps1 -ProjectId <project-id> -ServiceHost <service-url-host>
    (ServiceHost is the Cloud Run service's hostname, e.g. the host part of 'gcloud run services describe ... --format value(status.url)')

  What it does, in order:
    1 create (or update) each log-based metric in deploy/monitoring/log-metrics.json
    2 create the uptime check for /ready (deploy/monitoring/alert-policies.json, uptimeChecks), unless it exists
    3 create each alert policy in deploy/monitoring/alert-policies.json (an existing policy with the same name is
      replaced). Notification channels are left empty: the user adds them later in the Cloud Console, because this
      script does not decide who gets paged.
    4 create the dashboard in deploy/monitoring/dashboard.json (an existing one with the same name is replaced)

  It calls the Cloud Logging and Cloud Monitoring REST APIs with your gcloud access token. gcloud commands with
  quoted filters were avoided on purpose: Windows PowerShell 5.1 passes double quotes to native commands in a broken form.
  The script can be run again; it does not create duplicates.
  The thresholds in alert-policies.json are placeholders (no measured basis).
#>
param(
    [Parameter(Mandatory = $true)][string]$ProjectId,
    [Parameter(Mandatory = $true)][string]$ServiceHost
)

$ErrorActionPreference = 'Stop'

function Get-AccessToken {
    # gcloud writes progress to stderr, which Windows PowerShell 5.1 turns into an error while the preference is 'Stop'.
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    # Do not cut the pipeline short (Select-Object -First 1): that makes the exit code -1.
    try { $value = (& gcloud auth print-access-token 2>$null | Out-String) } finally { $ErrorActionPreference = $previous }
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($value)) { throw 'Run gcloud auth login first.' }
    return $value.Trim()
}

$script:Headers = @{ Authorization = ('Bearer ' + (Get-AccessToken)); 'x-goog-user-project' = $ProjectId }

function Invoke-Api([string]$Method, [string]$Uri, $Body) {
    $params = @{ Method = $Method; Uri = $Uri; Headers = $script:Headers; ContentType = 'application/json; charset=utf-8' }
    if ($null -ne $Body) {
        # UTF-8 bytes: the descriptions contain Japanese text
        $params.Body = [System.Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 30 -Compress))
    }
    try {
        return Invoke-RestMethod @params
    }
    catch {
        $status = 0
        if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
        $detail = $_.Exception.Message
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) { $detail = $_.ErrorDetails.Message }
        $failure = New-Object System.Exception("HTTP $status $Method $Uri : $detail")
        $failure.Data['status'] = $status
        throw $failure
    }
}

function Read-JsonFile([string]$path) {
    if (-not (Test-Path $path)) { throw "missing config file: $path" }
    return Get-Content $path -Raw -Encoding UTF8 | ConvertFrom-Json
}

function Get-Seconds($value, [int]$fallback) {
    if ($null -eq $value) { return "$($fallback)s" }
    return "$([int]$value)s"
}

$loggingBase = "https://logging.googleapis.com/v2/projects/$ProjectId"
$monitoringBase = "https://monitoring.googleapis.com/v3/projects/$ProjectId"
$dashboardBase = "https://monitoring.googleapis.com/v1/projects/$ProjectId"
$metricTypePrefix = 'logging.googleapis.com/user/'

Write-Host "Project: $ProjectId / Service host: $ServiceHost"

$metricsConfig = Read-JsonFile (Join-Path $PSScriptRoot 'monitoring\log-metrics.json')
$alertConfig = Read-JsonFile (Join-Path $PSScriptRoot 'monitoring\alert-policies.json')
$dashboardConfig = Read-JsonFile (Join-Path $PSScriptRoot 'monitoring\dashboard.json')

# 1 log-based metrics
foreach ($metric in $metricsConfig.metrics) {
    $isDistribution = ($metric.valueType -eq 'DISTRIBUTION')
    $descriptor = @{ metricKind = $metric.metricKind; valueType = $metric.valueType; unit = $(if ($isDistribution) { 'ms' } else { '1' }) }
    $labels = @()
    $labelExtractors = @{}
    if ($metric.labelExtractors) {
        foreach ($property in $metric.labelExtractors.PSObject.Properties) {
            $labels += @{ key = $property.Name; valueType = 'STRING' }
            $labelExtractors[$property.Name] = $property.Value
        }
    }
    if ($labels.Count -gt 0) { $descriptor.labels = $labels }
    $body = @{ name = $metric.name; description = $metric.description; filter = $metric.filter; metricDescriptor = $descriptor }
    if ($labelExtractors.Count -gt 0) { $body.labelExtractors = $labelExtractors }
    if ($metric.valueExtractor) { $body.valueExtractor = $metric.valueExtractor }
    if ($metric.bucketOptions) { $body.bucketOptions = $metric.bucketOptions }
    try {
        Invoke-Api 'POST' "$loggingBase/metrics" $body | Out-Null
        Write-Host "Metric created: $($metric.name)"
    }
    catch {
        if ($_.Exception.Data['status'] -ne 409) { throw }
        Invoke-Api 'PUT' "$loggingBase/metrics/$($metric.name)" $body | Out-Null
        Write-Host "Metric updated: $($metric.name)"
    }
}

# 2 uptime check for /ready
$existingChecks = @()
$checkList = Invoke-Api 'GET' "$monitoringBase/uptimeCheckConfigs" $null
if ($checkList.uptimeCheckConfigs) { $existingChecks = @($checkList.uptimeCheckConfigs) }
$checkIds = @{}
foreach ($check in $alertConfig.uptimeChecks) {
    $found = $existingChecks | Where-Object { $_.displayName -eq $check.name } | Select-Object -First 1
    if ($found) {
        $checkIds[$check.name] = ($found.name -split '/')[-1]
        Write-Host "Uptime check exists: $($check.name) ($($checkIds[$check.name]))"
        continue
    }
    $body = @{
        displayName       = $check.name
        monitoredResource = @{ type = 'uptime_url'; labels = @{ project_id = $ProjectId; host = $ServiceHost } }
        httpCheck         = @{ requestMethod = 'GET'; useSsl = $true; path = $check.path; port = 443; validateSsl = $true }
        period            = (Get-Seconds $check.periodSeconds 60)
        timeout           = (Get-Seconds $check.timeoutSeconds 10)
    }
    $created = Invoke-Api 'POST' "$monitoringBase/uptimeCheckConfigs" $body
    $checkIds[$check.name] = ($created.name -split '/')[-1]
    Write-Host "Uptime check created: $($check.name) ($($checkIds[$check.name]))"
}

# 3 alert policies (thresholds are placeholders; see the _comment field in alert-policies.json)
$existingPolicies = @()
$policyList = Invoke-Api 'GET' "$monitoringBase/alertPolicies" $null
if ($policyList.alertPolicies) { $existingPolicies = @($policyList.alertPolicies) }
foreach ($policy in $alertConfig.policies) {
    $period = Get-Seconds $policy.alignmentPeriodSeconds 60
    if ($policy.uptimeCheck) {
        $filter = 'metric.type="monitoring.googleapis.com/uptime_check/check_passed" AND metric.label.check_id="' + $checkIds[$policy.uptimeCheck] + '" AND resource.type="uptime_url"'
        $aggregation = @{ alignmentPeriod = '1200s'; perSeriesAligner = 'ALIGN_NEXT_OLDER'; crossSeriesReducer = 'REDUCE_COUNT_FALSE'; groupByFields = @('resource.label.*') }
    }
    else {
        $filter = 'metric.type="' + $metricTypePrefix + $policy.metric + '" AND resource.type="cloud_run_revision"'
        $aggregation = @{ alignmentPeriod = $period; perSeriesAligner = $policy.aligner }
        if ($policy.reducer -and $policy.reducer -ne 'REDUCE_NONE') { $aggregation.crossSeriesReducer = $policy.reducer }
    }
    $condition = @{
        displayName        = $policy.name
        conditionThreshold = @{
            filter          = $filter
            aggregations    = @($aggregation)
            comparison      = $policy.comparison
            thresholdValue  = [double]$policy.thresholdValue
            duration        = (Get-Seconds $policy.durationSeconds 0)
            trigger         = @{ count = 1 }
        }
    }
    $body = @{
        displayName          = $policy.name
        documentation        = @{ content = $policy.description; mimeType = 'text/markdown' }
        combiner             = 'OR'
        enabled              = $true
        conditions           = @($condition)
        notificationChannels = @()
    }
    foreach ($old in ($existingPolicies | Where-Object { $_.displayName -eq $policy.name })) {
        Invoke-Api 'DELETE' "https://monitoring.googleapis.com/v3/$($old.name)" $null | Out-Null
    }
    Invoke-Api 'POST' "$monitoringBase/alertPolicies" $body | Out-Null
    Write-Host "Alert policy created: $($policy.name) (placeholder threshold, not measured)"
}

# 4 dashboard: one line chart per widget; a distribution metric shows p50 and p95, a count metric shows the count per minute
$metricByName = @{}
foreach ($metric in $metricsConfig.metrics) { $metricByName[$metric.name] = $metric }
$tiles = @()
$index = 0
foreach ($widget in $dashboardConfig.widgets) {
    $metric = $metricByName[$widget.metric]
    $metricFilter = 'metric.type="' + $metricTypePrefix + $widget.metric + '" AND resource.type="cloud_run_revision"'
    $dataSets = @()
    if ($metric.valueType -eq 'DISTRIBUTION') {
        foreach ($aligner in @('ALIGN_PERCENTILE_50', 'ALIGN_PERCENTILE_95')) {
            $dataSets += @{ plotType = 'LINE'; timeSeriesQuery = @{ timeSeriesFilter = @{ filter = $metricFilter; aggregation = @{ alignmentPeriod = '60s'; perSeriesAligner = $aligner } } } }
        }
    }
    else {
        $aggregation = @{ alignmentPeriod = '60s'; perSeriesAligner = 'ALIGN_SUM'; crossSeriesReducer = 'REDUCE_SUM' }
        if ($metric.labelExtractors) {
            $firstLabel = @($metric.labelExtractors.PSObject.Properties)[0].Name
            $aggregation.groupByFields = @('metric.label.' + $firstLabel)
        }
        $dataSets += @{ plotType = 'LINE'; timeSeriesQuery = @{ timeSeriesFilter = @{ filter = $metricFilter; aggregation = $aggregation } } }
    }
    $tiles += @{
        xPos   = ($index % 2) * 6
        yPos   = [int][math]::Floor($index / 2) * 4
        width  = 6
        height = 4
        widget = @{ title = $widget.title; xyChart = @{ dataSets = $dataSets; yAxis = @{ scale = 'LINEAR' } } }
    }
    $index++
}
$dashboardBody = @{ displayName = $dashboardConfig.displayName; mosaicLayout = @{ columns = 12; tiles = $tiles } }
$dashboardList = Invoke-Api 'GET' "$dashboardBase/dashboards" $null
if ($dashboardList.dashboards) {
    foreach ($old in (@($dashboardList.dashboards) | Where-Object { $_.displayName -eq $dashboardConfig.displayName })) {
        Invoke-Api 'DELETE' "https://monitoring.googleapis.com/v1/$($old.name)" $null | Out-Null
    }
}
Invoke-Api 'POST' "$dashboardBase/dashboards" $dashboardBody | Out-Null
Write-Host "Dashboard created: $($dashboardConfig.displayName)"

Write-Host 'Done. Notification channels are empty: add them in the Cloud Console to be told when an alert fires.'
