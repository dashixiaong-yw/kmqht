# Sync Script - Local to docker-deploy
# 同步后端部署文件到 docker-deploy 根目录（Docker build context）

param(
    [switch]$Force,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path $PSScriptRoot -Parent
$BackendDir = Join-Path $ProjectRoot "backend"
$DockerDeployRoot = Join-Path $ProjectRoot "docker-deploy"

Write-Host "========================================="
Write-Host "  Sync Script - Local to docker-deploy"
Write-Host "========================================="
Write-Host ""

if (-not (Test-Path -LiteralPath $DockerDeployRoot)) {
    Write-Host "INFO: Creating docker-deploy dir: $DockerDeployRoot"
    New-Item -ItemType Directory -Path $DockerDeployRoot -Force | Out-Null
}

Write-Host "INFO: Project Root: $ProjectRoot"
Write-Host "INFO: Backend Dir: $BackendDir"
Write-Host "INFO: Docker Deploy: $DockerDeployRoot"
Write-Host ""

$totalFiles = 0
$syncCount = 0
$skipCount = 0
$errorCount = 0

function Sync-Directory {
    param([string]$SourceDir, [string]$TargetDir, [string]$Label)

    if (-not (Test-Path -LiteralPath $SourceDir)) {
        Write-Host "WARN: Source dir not found: $SourceDir"
        return
    }

    Write-Host "-----------------------------------------"
    Write-Host "INFO: Syncing $Label directory"
    Write-Host "INFO: From: $SourceDir"
    Write-Host "INFO: To: $TargetDir"
    Write-Host ""

    $files = Get-ChildItem -LiteralPath $SourceDir -Recurse -File | Where-Object {
        $_.FullName -notmatch '__pycache__' -and
        $_.FullName -notmatch '\.git' -and
        $_.FullName -notmatch '\.venv' -and
        $_.FullName -notmatch '\.pyc$'
    }

    foreach ($file in $files) {
        $relativePath = $file.FullName.Substring($SourceDir.Length + 1)
        $targetFile = Join-Path $TargetDir $relativePath
        $targetFileDir = Split-Path $targetFile -Parent

        if (-not (Test-Path -LiteralPath $targetFileDir)) {
            if (-not $DryRun) {
                New-Item -ItemType Directory -Path $targetFileDir -Force | Out-Null
            }
        }

        $script:totalFiles++

        if ($DryRun) {
            Write-Host "DRYRUN: Would copy: $Label/$relativePath"
            $script:syncCount++
        }
        else {
            $shouldCopy = $true
            if ((Test-Path -LiteralPath $targetFile) -and -not $Force) {
                $sourceHash = (Get-FileHash -LiteralPath $file.FullName -Algorithm MD5).Hash
                $destHash = (Get-FileHash -LiteralPath $targetFile -Algorithm MD5).Hash
                if ($sourceHash -eq $destHash) {
                    $shouldCopy = $false
                    $script:skipCount++
                }
            }

            if ($shouldCopy) {
                try {
                    Copy-Item -LiteralPath $file.FullName -Destination $targetFile -Force
                    Write-Host "OK: $Label/$relativePath"
                    $script:syncCount++
                }
                catch {
                    Write-Host "ERROR: Failed to copy $Label/$relativePath : $($_.Exception.Message)"
                    $script:errorCount++
                }
            }
            else {
                Write-Host "SKIP: $Label/$relativePath (no changes)"
            }
        }
    }

    # cleanup orphans
    if (Test-Path -LiteralPath $TargetDir) {
        $deployFiles = Get-ChildItem -LiteralPath $TargetDir -Recurse -File | Where-Object {
            $_.FullName -notmatch '__pycache__' -and
            $_.FullName -notmatch '\.git' -and
            $_.FullName -notmatch '\.venv' -and
            $_.FullName -notmatch '\.pyc$'
        }

        foreach ($deployFile in $deployFiles) {
            $relativePath = $deployFile.FullName.Substring($TargetDir.Length + 1)
            $sourceFile = Join-Path $SourceDir $relativePath

            if (-not (Test-Path -LiteralPath $sourceFile)) {
                if ($DryRun) {
                    Write-Host "DRYRUN: Would delete orphan: $Label/$relativePath"
                }
                else {
                    try {
                        Remove-Item -LiteralPath $deployFile.FullName -Force
                        Write-Host "DELETE: Orphan: $Label/$relativePath"
                    }
                    catch {
                        Write-Host "ERROR: Failed to delete orphan $Label/$relativePath : $($_.Exception.Message)"
                        $script:errorCount++
                    }
                }
            }
        }
    }
    Write-Host ""
}

function Sync-File {
    param([string]$SourceFile, [string]$TargetFile, [string]$Label)

    if (-not (Test-Path -LiteralPath $SourceFile)) {
        Write-Host "WARN: Source not found: $Label, skipping"
        return
    }

    $script:totalFiles++

    if ($DryRun) {
        Write-Host "DRYRUN: Would copy: $Label"
        $script:syncCount++
    }
    else {
        $shouldCopy = $true
        if ((Test-Path -LiteralPath $TargetFile) -and -not $Force) {
            $sourceHash = (Get-FileHash -LiteralPath $SourceFile -Algorithm MD5).Hash
            $destHash = (Get-FileHash -LiteralPath $TargetFile -Algorithm MD5).Hash
            if ($sourceHash -eq $destHash) {
                $shouldCopy = $false
                $script:skipCount++
            }
        }

        if ($shouldCopy) {
            try {
                Copy-Item -LiteralPath $SourceFile -Destination $TargetFile -Force
                Write-Host "OK: $Label"
                $script:syncCount++
            }
            catch {
                Write-Host "ERROR: Failed to copy $Label : $($_.Exception.Message)"
                $script:errorCount++
            }
        }
        else {
            Write-Host "SKIP: $Label (no changes)"
        }
    }
}

# Sync subdirectories
$appSource = Join-Path $BackendDir "app"
$appTarget = Join-Path $DockerDeployRoot "app"
Sync-Directory -SourceDir $appSource -TargetDir $appTarget -Label "app"

# Sync root-level config files
$configFiles = @(
    @{ Name = "main.py"; Src = (Join-Path $BackendDir "main.py"); Dst = (Join-Path $DockerDeployRoot "main.py") }
    @{ Name = "requirements.txt"; Src = (Join-Path $BackendDir "requirements.txt"); Dst = (Join-Path $DockerDeployRoot "requirements.txt") }
    @{ Name = "Dockerfile"; Src = (Join-Path $BackendDir "Dockerfile"); Dst = (Join-Path $DockerDeployRoot "Dockerfile") }
    @{ Name = ".dockerignore"; Src = (Join-Path $BackendDir ".dockerignore"); Dst = (Join-Path $DockerDeployRoot ".dockerignore") }
    @{ Name = ".env.docker.example"; Src = (Join-Path $BackendDir ".env.docker.example"); Dst = (Join-Path $DockerDeployRoot ".env.docker.example") }
    @{ Name = "kuaimai.example.json"; Src = (Join-Path $BackendDir "kuaimai.example.json"); Dst = (Join-Path $DockerDeployRoot "kuaimai.example.json") }
    @{ Name = "kuaimai.json"; Src = (Join-Path $BackendDir "kuaimai.json"); Dst = (Join-Path $DockerDeployRoot "kuaimai.json") }
    @{ Name = "docker-compose.yml"; Src = (Join-Path $BackendDir "docker-compose.yml"); Dst = (Join-Path $DockerDeployRoot "docker-compose.yml") }
)

foreach ($cfg in $configFiles) {
    Sync-File -SourceFile $cfg.Src -TargetFile $cfg.Dst -Label $cfg.Name
}

# docker-compose.yml -> docker-compose.yaml
$yamlSource = Join-Path $DockerDeployRoot "docker-compose.yml"
$yamlTarget = Join-Path $DockerDeployRoot "docker-compose.yaml"
if (Test-Path -LiteralPath $yamlSource) {
    $shouldCopyYaml = $true
    if (-not $DryRun) {
        if ((Test-Path -LiteralPath $yamlTarget) -and -not $Force) {
            $sourceHash = (Get-FileHash -LiteralPath $yamlSource -Algorithm MD5).Hash
            $destHash = (Get-FileHash -LiteralPath $yamlTarget -Algorithm MD5).Hash
            if ($sourceHash -eq $destHash) {
                $shouldCopyYaml = $false
            }
        }
    }

    if ($DryRun) {
        Write-Host "DRYRUN: Would copy: docker-compose.yml -> docker-compose.yaml"
    }
    elseif ($shouldCopyYaml) {
        Copy-Item -LiteralPath $yamlSource -Destination $yamlTarget -Force
        Write-Host "OK: docker-compose.yml -> docker-compose.yaml"
        $totalFiles++
        $syncCount++
    }
    else {
        Write-Host "SKIP: docker-compose.yaml (no changes)"
    }
}

# Note: docker-deploy/kuaimai.json ignored by Docker (uses data/kuaimai.json)
# To avoid confusion, manually delete the root copy.

Write-Host ""
Write-Host "========================================="
Write-Host "SUMMARY"
Write-Host "  Total Files: $totalFiles"
if ($DryRun) {
    Write-Host "  Would sync: $syncCount files"
}
else {
    Write-Host "  Synced: $syncCount files"
    Write-Host "  Skipped: $skipCount files (no changes)"
    if ($errorCount -gt 0) {
        Write-Host "  Errors: $errorCount files"
    }
}

# Post-sync: validate docker-compose port matches SERVER_PORT in .env
$dcPath = Join-Path $DockerDeployRoot "docker-compose.yml"
$envPath = Join-Path $DockerDeployRoot ".env.docker.example"
if ((Test-Path $dcPath) -and (Test-Path $envPath)) {
    $portFromEnv = (Select-String -Path $envPath -Pattern "^SERVER_PORT=(\d+)" | ForEach-Object { $_.Matches.Groups[1].Value })
    if ($portFromEnv) {
        $expectedMapping = "`"8900:$portFromEnv`""
        $dcContent = Get-Content $dcPath -Raw
        if ($dcContent -notmatch $expectedMapping) {
            Write-Host "WARN: docker-compose port mapping may not match SERVER_PORT=$portFromEnv"
            Write-Host "  Expected mapping: $expectedMapping"
            Write-Host "  Run: sed to fix or manually edit docker-compose.yml"
        }
    }
}
Write-Host "========================================="
Write-Host ""

if ($errorCount -gt 0) {
    exit 1
}
else {
    exit 0
}
