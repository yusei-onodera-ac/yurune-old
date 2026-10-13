<#
  Check the deployed environment against the security and availability design.
  (ASCII only on purpose: Windows PowerShell 5.1 reads a file without a BOM as ANSI.)

  Usage:
    .\deploy\verify.ps1 -ProjectId <project-id> [-Region asia-northeast1] [-ServiceName yurune]

  Checks (each prints PASS or FAIL; the script exits with 1 when any check fails):
    1 the runtime service account has no Owner / Editor / Admin role, and only the roles we expect
    2 the Cloud Run service uses that account (not the default compute account) and has an instance cap
    3 the secret is mounted from Secret Manager (no API key in a plain environment variable)
    4 the public URL answers with the security headers (CSP, nosniff, frame protection, no-store, HSTS)
    5 anonymous requests to a protected page are redirected to the login page
    6 the latest image has no CRITICAL vulnerability in Artifact Registry's scan result
    7 the log-based metrics in deploy/monitoring/log-metrics.json exist
    8 the alert policies in deploy/monitoring/alert-policies.json exist
    9 /ready answers (does not check its status code; a 503 there can be a true, if degraded, reading)

  Run against a real project on 2026-10-12. Checks 7 and 8 fail until deploy/apply-monitoring.ps1 has been run.
#>
param(
    [Parameter(Mandatory = $true)][string]$ProjectId,
    [string]$Region = 'asia-northeast1',
    [string]$ServiceName = 'yurune',
    [string]$ServiceAccountName = 'yurune-app'
)

$ErrorActionPreference = 'Stop'
$failed = $false
function Report([string]$name, [bool]$ok, [string]$detail = '') {
    if ($ok) { Write-Host "PASS  $name $detail" } else { Write-Host "FAIL  $name $detail"; $script:failed = $true }
}

$serviceAccount = "$ServiceAccountName@$ProjectId.iam.gserviceaccount.com"

# 1 roles of the runtime account
$policy = & gcloud projects get-iam-policy $ProjectId --format json | ConvertFrom-Json
$granted = @()
foreach ($binding in $policy.bindings) {
    if ($binding.members -contains "serviceAccount:$serviceAccount") { $granted += $binding.role }
}
Write-Host ("Roles of ${serviceAccount}: " + ($granted -join ', '))
$tooBroad = @($granted | Where-Object { $_ -match '^roles/(owner|editor)$' -or $_ -match 'admin' })
Report 'runtime account has no Owner, Editor or Admin role' ($tooBroad.Count -eq 0) ($tooBroad -join ', ')
$expected = @('roles/datastore.user', 'roles/logging.logWriter', 'roles/cloudtrace.agent', 'roles/serviceusage.serviceUsageConsumer', 'roles/modelarmor.user')
$unexpected = @($granted | Where-Object { $expected -notcontains $_ })
Report 'runtime account has only the expected roles' ($unexpected.Count -eq 0) ($unexpected -join ', ')

# 2 and 3 Cloud Run settings
$service = & gcloud run services describe $ServiceName --region $Region --format json | ConvertFrom-Json
$template = $service.spec.template
Report 'Cloud Run uses the dedicated service account' ($template.spec.serviceAccountName -eq $serviceAccount) $template.spec.serviceAccountName
$maxScale = $template.metadata.annotations.'autoscaling.knative.dev/maxScale'
Report 'Cloud Run has an instance cap' (-not [string]::IsNullOrEmpty($maxScale)) "maxScale=$maxScale"
$container = $template.spec.containers[0]
$plainKeys = @($container.env | Where-Object { $_.name -match 'KEY|SECRET|PASSWORD|TOKEN' -and $_.value -and $_.name -ne 'FIREBASE_API_KEY' })
Report 'no secret is stored as a plain environment variable' ($plainKeys.Count -eq 0) (($plainKeys | ForEach-Object { $_.name }) -join ', ')
$secretRef = @($container.env | Where-Object { $_.name -eq 'GEMINI_API_KEY' -and $_.valueFrom.secretKeyRef })
Report 'the Gemini key comes from Secret Manager' ($secretRef.Count -eq 1)

# 4 and 5 live behaviour
$url = $service.status.url
try {
    $page = Invoke-WebRequest -Uri "$url/login" -UseBasicParsing
    $h = $page.Headers
    Report 'Content-Security-Policy is set' ($h['Content-Security-Policy'] -match "script-src 'self'")
    Report 'X-Content-Type-Options is nosniff' ($h['X-Content-Type-Options'] -eq 'nosniff')
    Report 'framing is denied' ($h['X-Frame-Options'] -eq 'DENY')
    Report 'pages are not cached' ($h['Cache-Control'] -eq 'no-store')
    Report 'HSTS is set over HTTPS' ($null -ne $h['Strict-Transport-Security'])
    Report 'the Server header does not reveal the Tomcat version' ($h['Server'] -notmatch 'Tomcat|Coyote')
    $redirect = Invoke-WebRequest -Uri "$url/home" -UseBasicParsing -MaximumRedirection 0 -ErrorAction SilentlyContinue
    Report 'anonymous access to a protected page is redirected to login' ($redirect.StatusCode -eq 302 -and $redirect.Headers['Location'] -match '/login')
} catch {
    Report 'the service answers' $false $_.Exception.Message
}

# From here on, gcloud calls hide stderr with 2>$null. Windows PowerShell 5.1 turns that stderr text into a terminating
# error while $ErrorActionPreference is 'Stop', so the setting is relaxed; every result is judged through Report.
$ErrorActionPreference = 'Continue'

# 6 vulnerability scan
$images = & gcloud artifacts docker images list "$Region-docker.pkg.dev/$ProjectId/cloud-run-source-deploy" --include-tags --sort-by '~UPDATE_TIME' --limit 1 --format 'value(IMAGE)' 2>$null
if ($images) {
    $scan = & gcloud artifacts docker images describe $images --show-package-vulnerability --format json 2>$null | ConvertFrom-Json
    $critical = 0
    foreach ($item in $scan.package_vulnerability_summary.vulnerabilities.CRITICAL) { $critical++ }
    Report 'the latest image has no CRITICAL vulnerability' ($critical -eq 0) "critical=$critical (scan may still be running)"
} else {
    Report 'the latest image was found for the scan result' $false 'no image listed; check the Artifact Registry repository name'
}

# 7 and 8 observability (milestone O2, item B3): metrics and alert policies exist
$metricsConfigPath = Join-Path $PSScriptRoot 'monitoring\log-metrics.json'
if (Test-Path $metricsConfigPath) {
    $metricsConfig = Get-Content $metricsConfigPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $existingMetrics = & gcloud logging metrics list --format 'value(name)' 2>$null
    foreach ($metric in $metricsConfig.metrics) {
        Report "log-based metric exists: $($metric.name)" ($existingMetrics -contains $metric.name)
    }
} else {
    Report 'deploy/monitoring/log-metrics.json is present' $false
}

$alertConfigPath = Join-Path $PSScriptRoot 'monitoring\alert-policies.json'
if (Test-Path $alertConfigPath) {
    $alertConfig = Get-Content $alertConfigPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $existingPolicies = & gcloud alpha monitoring policies list --format 'value(displayName)' 2>$null
    foreach ($policy in $alertConfig.policies) {
        Report "alert policy exists: $($policy.name)" ($existingPolicies -contains $policy.name)
    }
} else {
    Report 'deploy/monitoring/alert-policies.json is present' $false
}

# 9 /ready answers (a 503 is a true, if degraded, reading - not treated as a failure here)
try {
    $ready = Invoke-WebRequest -Uri "$url/ready" -UseBasicParsing
    Report '/ready answers' $true "status=$($ready.StatusCode)"
} catch {
    $readyStatus = $null
    if ($_.Exception.Response) { $readyStatus = [int]$_.Exception.Response.StatusCode }
    Report '/ready answers' ($readyStatus -eq 503) "status=$readyStatus"
}

if ($failed) { exit 1 }
