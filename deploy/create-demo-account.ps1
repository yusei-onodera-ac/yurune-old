<#
  Create the review (demo) account: an email/password account that is already verified, so a reviewer can sign in
  without receiving any email. It uses the Identity Toolkit admin endpoint with your own gcloud credentials.
  (ASCII only on purpose: Windows PowerShell 5.1 reads a file without a BOM as ANSI.)

  Usage:
    gcloud auth login
    $env:YURUNE_DEMO_PASSWORD = '<password>'
    .\deploy\create-demo-account.ps1 -ProjectId <project-id> -Email review@yurune.example

  The password is only read from the environment or the parameter. It is not written anywhere by this script.
  The account gets no special rights: it is an ordinary user. The app recognizes it by its email address
  (YURUNE_DEMO_EMAIL) and puts the sample data into its own folder, users/{uid}, on its first sign-in.

  Run against a real project on 2026-10-12 (the account could sign in). The same call is also checked against the
  Firebase Authentication emulator.
#>
param(
    [Parameter(Mandatory = $true)][string]$ProjectId,
    [Parameter(Mandatory = $true)][string]$Email,
    [string]$Password = $env:YURUNE_DEMO_PASSWORD
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($Password)) { throw 'Set YURUNE_DEMO_PASSWORD (environment variable or -Password).' }

$token = (& gcloud auth print-access-token).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Run gcloud auth login first.' }

$body = @{ email = $Email; password = $Password; emailVerified = $true } | ConvertTo-Json -Compress
$uri = "https://identitytoolkit.googleapis.com/v1/projects/$ProjectId/accounts"
$headers = @{ Authorization = "Bearer $token"; 'X-Goog-User-Project' = $ProjectId }

$response = Invoke-RestMethod -Method Post -Uri $uri -Headers $headers -ContentType 'application/json' -Body $body
Write-Host "Created the review account (uid: $($response.localId))."
