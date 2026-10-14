<#
  Publish the landing page (the files in dist) to Firebase Hosting, with the Firebase Hosting REST API and the
  access token of the signed-in gcloud account. The Firebase CLI is not needed.
  (ASCII only on purpose: Windows PowerShell 5.1 reads a file without a BOM as ANSI.)

  Usage:
    gcloud auth login
    node build.js
    .\publish.ps1 -ProjectId <project-id>

  What it does, in order:
    1 creates a new version of the site, with security headers and cache rules
    2 sends only the files the site does not have yet (gzip, addressed by hash)
    3 finalizes the version and releases it. The old version stays in the release history.
  The site is the default site of the project (https://<project-id>.web.app).
#>
param(
    [Parameter(Mandatory = $true)][string]$ProjectId,
    [string]$SiteId = $ProjectId,
    [string]$DistDir = ''
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($DistDir)) { $DistDir = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) 'dist' }
$api = 'https://firebasehosting.googleapis.com/v1beta1'

$previous = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$token = (& gcloud auth print-access-token 2>$null | Out-String).Trim()
$ErrorActionPreference = $previous
if ([string]::IsNullOrWhiteSpace($token)) { throw 'could not get an access token: run gcloud auth login' }
$headers = @{ Authorization = "Bearer $token"; 'x-goog-user-project' = $ProjectId }

function Invoke-Api([string]$Method, [string]$Uri, $Body) {
    $params = @{ Method = $Method; Uri = $Uri; Headers = $headers }
    if ($null -ne $Body) {
        $params.Body = [System.Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 10 -Compress))
        $params.ContentType = 'application/json; charset=utf-8'
    }
    try { return Invoke-RestMethod @params }
    catch {
        $detail = ''
        try { $detail = $_.ErrorDetails.Message } catch { }
        throw ("API call failed: $Method $Uri " + $_.Exception.Message + ' ' + $detail)
    }
}

# The page has no script. Styles are inline, fonts come from Google Fonts, images are the page's own.
$csp = "default-src 'none'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src https://fonts.gstatic.com; img-src 'self' data:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'"
$config = @{
    headers = @(
        @{ glob = '**'; headers = @{
            'Content-Security-Policy' = $csp
            'X-Content-Type-Options'  = 'nosniff'
            'X-Frame-Options'         = 'DENY'
            'Referrer-Policy'         = 'strict-origin-when-cross-origin'
            'Permissions-Policy'      = 'camera=(), microphone=(), geolocation=(), payment=()'
        } },
        @{ glob = '/'; headers = @{ 'Cache-Control' = 'no-cache' } },
        @{ glob = '**/*.html'; headers = @{ 'Cache-Control' = 'no-cache' } },
        @{ glob = '/images/**'; headers = @{ 'Cache-Control' = 'public, max-age=86400' } }
    )
}

# gzip every file; the hash of the gzipped bytes is how the site knows a file
$sha = [System.Security.Cryptography.SHA256]::Create()
$files = @{}
$payloads = @{}
$root = (Resolve-Path $DistDir).Path.TrimEnd('\')
foreach ($file in Get-ChildItem -Path $root -Recurse -File) {
    $relative = '/' + $file.FullName.Substring($root.Length + 1).Replace('\', '/')
    $memory = New-Object System.IO.MemoryStream
    $zip = New-Object System.IO.Compression.GZipStream($memory, [System.IO.Compression.CompressionMode]::Compress)
    $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
    $zip.Write($bytes, 0, $bytes.Length)
    $zip.Close()
    $gzipped = $memory.ToArray()
    $hash = [BitConverter]::ToString($sha.ComputeHash($gzipped)).Replace('-', '').ToLower()
    $files[$relative] = $hash
    $payloads[$hash] = $gzipped
}
if (-not $files.ContainsKey('/index.html')) { throw "dist has no index.html: run node build.js first ($root)" }
Write-Host ("files: " + $files.Count)

$version = Invoke-Api 'POST' "$api/sites/$SiteId/versions" @{ config = $config }
Write-Host ("version: " + $version.name)

$populate = Invoke-Api 'POST' "$api/$($version.name):populateFiles" @{ files = $files }
$required = @($populate.uploadRequiredHashes | Where-Object { $_ })
Write-Host ("files to send: " + $required.Count)
foreach ($hash in $required) {
    Invoke-RestMethod -Method Post -Uri ($populate.uploadUrl + '/' + $hash) -Headers $headers -ContentType 'application/octet-stream' -Body $payloads[$hash] | Out-Null
}

Invoke-Api 'PATCH' "$api/$($version.name)?update_mask=status" @{ status = 'FINALIZED' } | Out-Null
$release = Invoke-Api 'POST' "$api/sites/$SiteId/releases?versionName=$($version.name)" @{}
Write-Host ("released: " + $release.name)
Write-Host ("Done. https://$SiteId.web.app")
