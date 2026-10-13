<#
  Create (or replace) a monthly budget alert for the project's costs.
  (ASCII only on purpose: Windows PowerShell 5.1 reads a file without a BOM as ANSI.)

  Usage:
    gcloud auth login
    .\deploy\apply-budget.ps1 -BillingAccountId <billing-account-id> -ProjectId <project-id> [-AmountJpy 10000]

  What it does:
    - enables the Cloud Billing Budget API
    - creates a budget named "yurune" for this project only. The amount is counted before credits are subtracted
      (EXCLUDE_ALL_CREDITS), so the alert shows the real usage even while credits pay for it.
    - alerts at 50%, 90% and 100% of the amount (current spend). A budget only alerts: it does not stop any spending.
    - sends the alert to the email notification channel "yurune-alert-email" (made by apply-monitoring.ps1 with
      -NotificationEmail) and to the default recipients (billing account administrators, project owners).
    - an existing budget with the same name is replaced.

  The amount is a placeholder (no measured basis). The currency is the billing account's (JPY here).
#>
param(
    [Parameter(Mandatory = $true)][string]$BillingAccountId,
    [Parameter(Mandatory = $true)][string]$ProjectId,
    [int]$AmountJpy = 10000,
    [string]$BudgetName = 'yurune',
    [string]$ChannelName = 'yurune-alert-email'
)

$ErrorActionPreference = 'Stop'

function Invoke-GcloudText {
    # Run gcloud and return its output as text. Native stderr must not stop the script in Windows PowerShell 5.1,
    # and the pipeline must not be cut short (Select-Object -First 1 makes the exit code -1).
    $flat = @($args | ForEach-Object { $_ })
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $text = (& gcloud @flat 2>$null | Out-String) } finally { $ErrorActionPreference = $previous }
    if ($LASTEXITCODE -ne 0) { throw ("gcloud failed: " + ($flat -join ' ')) }
    return $text.Trim()
}

$previous = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
& gcloud services enable billingbudgets.googleapis.com --project $ProjectId 2>&1 | Out-Null
$ErrorActionPreference = $previous
if ($LASTEXITCODE -ne 0) { throw 'could not enable the Cloud Billing Budget API' }

$token = Invoke-GcloudText auth print-access-token
$projectNumber = Invoke-GcloudText projects describe $ProjectId '--format=value(projectNumber)'
$headers = @{ Authorization = "Bearer $token"; 'x-goog-user-project' = $ProjectId }

function Invoke-Api([string]$Method, [string]$Uri, $Body) {
    $params = @{ Method = $Method; Uri = $Uri; Headers = $headers; ContentType = 'application/json; charset=utf-8' }
    if ($null -ne $Body) { $params.Body = [System.Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 20 -Compress)) }
    for ($attempt = 1; $attempt -le 4; $attempt++) {
        try { return Invoke-RestMethod @params }
        catch {
            # the API may answer 403 for a short while after it has been enabled
            if ($attempt -eq 4) { throw }
            Start-Sleep -Seconds 8
        }
    }
}

# the email channel made by apply-monitoring.ps1
$channelList = Invoke-Api 'GET' "https://monitoring.googleapis.com/v3/projects/$ProjectId/notificationChannels" $null
$channel = $null
if ($channelList.notificationChannels) {
    $channel = @($channelList.notificationChannels) | Where-Object { $_.displayName -eq $ChannelName } | Select-Object -First 1
}
if (-not $channel) { throw "notification channel '$ChannelName' not found: run apply-monitoring.ps1 with -NotificationEmail first" }

$base = "https://billingbudgets.googleapis.com/v1/billingAccounts/$BillingAccountId/budgets"
$existing = Invoke-Api 'GET' $base $null
if ($existing.budgets) {
    foreach ($old in (@($existing.budgets) | Where-Object { $_.displayName -eq $BudgetName })) {
        Invoke-Api 'DELETE' "https://billingbudgets.googleapis.com/v1/$($old.name)" $null | Out-Null
        Write-Host "Budget replaced: $BudgetName"
    }
}

$budget = @{
    displayName       = $BudgetName
    budgetFilter      = @{ projects = @("projects/$projectNumber"); creditTypesTreatment = 'EXCLUDE_ALL_CREDITS'; calendarPeriod = 'MONTH' }
    amount            = @{ specifiedAmount = @{ currencyCode = 'JPY'; units = "$AmountJpy" } }
    thresholdRules    = @(
        @{ thresholdPercent = 0.5; spendBasis = 'CURRENT_SPEND' },
        @{ thresholdPercent = 0.9; spendBasis = 'CURRENT_SPEND' },
        @{ thresholdPercent = 1.0; spendBasis = 'CURRENT_SPEND' }
    )
    notificationsRule = @{ monitoringNotificationChannels = @($channel.name); disableDefaultIamRecipients = $false; enableProjectLevelRecipients = $true }
}
$created = Invoke-Api 'POST' $base $budget
Write-Host "Budget created: $($created.displayName) = $AmountJpy JPY per month (alerts at 50%, 90%, 100%)"
Write-Host 'Done. The amount is a placeholder. A budget only alerts; it does not stop spending.'
