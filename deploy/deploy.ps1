<#
  Deploy Yurune to Google Cloud (Cloud Run, Tokyo). The same steps rebuild the same environment from parameters.
  (This file is ASCII on purpose: Windows PowerShell 5.1 reads a file without a BOM as ANSI.)

  Usage (PowerShell):
    gcloud auth login
    $env:GEMINI_API_KEY = '<key>'          # stored in Secret Manager, never in the repository or in an env var of the service
    $env:FIREBASE_API_KEY = '<web api key>' # the Firebase Web API key (it is a public identifier, not a secret)
    .\deploy\deploy.ps1 -ProjectId <project-id> -AllowUnauthenticated

  What it does, in order:
    1 enable the APIs
    2 create a dedicated runtime service account and give it only the roles it needs (no Owner, no Editor)
    3 create the Firestore database, the Gemini secret, the Artifact Registry scan, and the Model Armor template
    4 keep logs for 30 days
    5 build with Cloud Build and deploy to Cloud Run with the dedicated service account and an instance cap

  Run against a real project (Tokyo) on 2026-10-12 and the steps above completed. Model Armor could not be created in
  Tokyo, so pass -ArmorLocation us-central1 (see deploy/README.md). Read the output of each step.
  The Firestore security rules are deployed with the Firebase CLI (see deploy/README.md).
#>
param(
    [Parameter(Mandatory = $true)][string]$ProjectId,
    [string]$Region = 'asia-northeast1',
    [string]$ServiceName = 'yurune',
    [string]$ServiceAccountName = 'yurune-app',
    [string]$GeminiApiKey = $env:GEMINI_API_KEY,
    [string]$FirebaseApiKey = $env:FIREBASE_API_KEY,
    [string]$DemoEmail = 'review@yurune.example',
    [string]$ArmorLocation = 'asia-northeast1',
    [string]$ArmorTemplate = 'yurune-template',
    [int]$MaxInstances = 3,
    [int]$LogRetentionDays = 30,
    [switch]$AllowUnauthenticated,
    [switch]$SkipModelArmor,
    [switch]$SkipSpeech
)

$ErrorActionPreference = 'Stop'

function Invoke-Gcloud {
    # Run gcloud and stop at the first failure, so a half-configured project is never deployed.
    # An array inside $args (the list of APIs) must be flattened first: PowerShell would otherwise pass it as ONE
    # argument joined with spaces, and gcloud would look for a single service with that long name.
    $flat = @($args | ForEach-Object { $_ })
    # Windows PowerShell 5.1 turns a native command's stderr text into a terminating error while $ErrorActionPreference is
    # 'Stop'. gcloud writes progress and "not found" answers to stderr, so success is judged by the exit code only.
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { & gcloud @flat } finally { $ErrorActionPreference = $previous }
    if ($LASTEXITCODE -ne 0) { throw ("gcloud failed: " + ($flat -join ' ')) }
}

function Test-Gcloud {
    # Run gcloud quietly and return $true when it succeeded (used to ask "does this exist already?").
    # "Not found" is a normal answer here, so stderr must not stop the script (see Invoke-Gcloud).
    $flat = @($args | ForEach-Object { $_ })
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { & gcloud @flat 2>&1 | Out-Null } finally { $ErrorActionPreference = $previous }
    return ($LASTEXITCODE -eq 0)
}

if ([string]::IsNullOrWhiteSpace($GeminiApiKey)) { throw 'Set GEMINI_API_KEY (environment variable or -GeminiApiKey).' }
if ([string]::IsNullOrWhiteSpace($FirebaseApiKey)) { throw 'Set FIREBASE_API_KEY (environment variable or -FirebaseApiKey).' }

$serviceAccount = "$ServiceAccountName@$ProjectId.iam.gserviceaccount.com"
$secretName = 'yurune-gemini-api-key'

Write-Host "Project: $ProjectId / Region: $Region / Service: $ServiceName / Runtime account: $serviceAccount"
Invoke-Gcloud config set project $ProjectId

# 1 APIs
$apis = @(
    'run.googleapis.com', 'cloudbuild.googleapis.com', 'artifactregistry.googleapis.com',
    'containerscanning.googleapis.com',      # vulnerability scan of every pushed image
    'firestore.googleapis.com', 'identitytoolkit.googleapis.com', 'secretmanager.googleapis.com',
    'logging.googleapis.com', 'cloudtrace.googleapis.com'
)
if (-not $SkipModelArmor) { $apis += 'modelarmor.googleapis.com' }
if (-not $SkipSpeech) { $apis += 'texttospeech.googleapis.com' }
Invoke-Gcloud services enable $apis

# 2 Runtime service account with the least privilege
if (-not (Test-Gcloud iam service-accounts describe $serviceAccount)) {
    Invoke-Gcloud iam service-accounts create $ServiceAccountName --display-name 'Yurune runtime (least privilege)'
}
$roles = @(
    'roles/datastore.user',                      # read and write user data in Firestore
    'roles/logging.logWriter',                   # write structured logs
    'roles/cloudtrace.agent',                    # write traces
    'roles/serviceusage.serviceUsageConsumer'    # call Text-to-Speech and Model Armor
)
if (-not $SkipModelArmor) { $roles += 'roles/modelarmor.user' }   # sanitize prompts and responses only
foreach ($role in $roles) {
    # A service account that was created a moment ago is not visible to IAM everywhere yet ("does not exist"), so retry a few times.
    $bound = $false
    for ($attempt = 1; ($attempt -le 8) -and (-not $bound); $attempt++) {
        if (Test-Gcloud projects add-iam-policy-binding $ProjectId --member "serviceAccount:$serviceAccount" --role $role --condition None --quiet) {
            $bound = $true
        } else {
            Start-Sleep -Seconds 10
        }
    }
    if (-not $bound) {
        # Still failing: run it once more without hiding the output, so the real reason is shown.
        Invoke-Gcloud projects add-iam-policy-binding $ProjectId --member "serviceAccount:$serviceAccount" --role $role --condition None --quiet
    }
    Write-Host "role bound: $role"
}

# 3a Firestore (Native mode, same region)
if (-not (Test-Gcloud firestore databases describe --database '(default)')) {
    Invoke-Gcloud firestore databases create --location $Region --type firestore-native
}

# 3b Gemini API key in Secret Manager. Only the runtime account may read this one secret.
if (-not (Test-Gcloud secrets describe $secretName)) {
    Invoke-Gcloud secrets create $secretName --replication-policy automatic
}
$keyFile = [System.IO.Path]::GetTempFileName()
try {
    [System.IO.File]::WriteAllText($keyFile, $GeminiApiKey)    # no trailing newline
    Invoke-Gcloud secrets versions add $secretName --data-file $keyFile
} finally {
    Remove-Item -Force $keyFile -ErrorAction SilentlyContinue
}
Invoke-Gcloud secrets add-iam-policy-binding $secretName --member "serviceAccount:$serviceAccount" --role roles/secretmanager.secretAccessor --quiet

# 3c Model Armor template (the app also runs its own checks; this is the second layer)
if (-not $SkipModelArmor -and -not (Test-Gcloud model-armor templates describe $ArmorTemplate --location $ArmorLocation)) {
    Invoke-Gcloud model-armor templates create $ArmorTemplate --location $ArmorLocation `
        --rai-settings-filters='[{ "filterType": "HATE_SPEECH", "confidenceLevel": "MEDIUM_AND_ABOVE" },{ "filterType": "HARASSMENT", "confidenceLevel": "MEDIUM_AND_ABOVE" },{ "filterType": "SEXUALLY_EXPLICIT", "confidenceLevel": "MEDIUM_AND_ABOVE" }]' `
        --basic-config-filter-enforcement=enabled `
        --pi-and-jailbreak-filter-settings-enforcement=enabled `
        --pi-and-jailbreak-filter-settings-confidence-level=MEDIUM_AND_ABOVE `
        --malicious-uri-filter-settings-enforcement=enabled
}

# 4 Log retention (decision D-8: 30 days)
Invoke-Gcloud logging buckets update _Default --location global --retention-days $LogRetentionDays

# 5 Build and deploy
$envVars = @(
    'YURUNE_STORAGE=firestore', 'YURUNE_AUTH=firebase', "GOOGLE_CLOUD_PROJECT=$ProjectId",
    "FIREBASE_API_KEY=$FirebaseApiKey", "YURUNE_DEMO_EMAIL=$DemoEmail",
    'YURUNE_NO_REACTION_MIN=15', 'YURUNE_VOLUME_CAP_PCT=60', 'YURUNE_OBSERVATION_INTERVAL_SEC=30'
)
if (-not $SkipModelArmor) { $envVars += "YURUNE_ARMOR_TEMPLATE=$ArmorTemplate"; $envVars += "YURUNE_ARMOR_LOCATION=$ArmorLocation" }
if (-not $SkipSpeech) { $envVars += 'YURUNE_TTS_ENABLED=true' }

$arguments = @(
    'run', 'deploy', $ServiceName,
    '--source', '.',
    '--region', $Region,
    '--service-account', $serviceAccount,
    '--max-instances', $MaxInstances,
    '--min-instances', 0,
    '--concurrency', 40,
    '--timeout', 60,
    '--memory', '1Gi',
    '--cpu', 1,
    '--set-secrets', "GEMINI_API_KEY=${secretName}:latest",
    '--set-env-vars', ($envVars -join ',')
)
if ($AllowUnauthenticated) { $arguments += '--allow-unauthenticated' } else { $arguments += '--no-allow-unauthenticated' }
Invoke-Gcloud @arguments

Write-Host 'URL:'
Invoke-Gcloud run services describe $ServiceName --region $Region --format 'value(status.url)'
Write-Host 'Next: deploy the Firestore rules, create the review account, then run deploy\verify.ps1 (see deploy\README.md).'
