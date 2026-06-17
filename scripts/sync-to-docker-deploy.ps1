# Sync Script - Local to docker-deploy
# 同步后端部署文件到 docker-deploy 目录

param(
    [switch]$Force,
    [switch]$DryRun
)

$ErrorActionPreference = "Continue"

$ProjectRoot = Split-Path $PSScriptRoot -Parent
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
Write-Host "INFO: Docker Deploy: $DockerDeployRoot"
Write-Host ""

$totalFiles = 0
$syncCount = 0
$skipCount = 0
$errorCount = 0

# 同步后端目录
$sourceDir = Join-Path $ProjectRoot "backend"
$targetDir = Join-Path $DockerDeployRoot "backend"

if (Test-Path -LiteralPath $sourceDir) {
    Write-Host "-----------------------------------------"
    Write-Host "INFO: Syncing backend directory"
    Write-Host "INFO: From: $sourceDir"
    Write-Host "INFO: To: $targetDir"
    Write-Host ""

    $files = Get-ChildItem -LiteralPath $sourceDir -Recurse -File | Where-Object {
        $_.FullName -notmatch '__pycache__' -and
        $_.FullName -notmatch '\.git' -and
        $_.FullName -notmatch '\.venv' -and
        $_.FullName -notmatch '\.pyc$'
    }

    $totalFiles += $files.Count

    foreach ($file in $files) {
        $relativePath = $file.FullName.Substring($sourceDir.Length + 1)
        $targetFile = Join-Path $targetDir $relativePath
        $targetFileDir = Split-Path $targetFile -Parent

        if (-not (Test-Path -LiteralPath $targetFileDir)) {
            if (-not $DryRun) {
                New-Item -ItemType Directory -Path $targetFileDir -Force | Out-Null
            }
        }

        if ($DryRun) {
            Write-Host "DRYRUN: Would copy: backend/$relativePath"
            $syncCount++
        } else {
            $shouldCopy = $true

            if ((Test-Path -LiteralPath $targetFile) -and -not $Force) {
                $sourceHash = (Get-FileHash -LiteralPath $file.FullName -Algorithm MD5).Hash
                $destHash = (Get-FileHash -LiteralPath $targetFile -Algorithm MD5).Hash
                if ($sourceHash -eq $destHash) {
                    $shouldCopy = $false
                    $skipCount++
                }
            }

            if ($shouldCopy) {
                try {
                    Copy-Item -LiteralPath $file.FullName -Destination $targetFile -Force
                    Write-Host "OK: backend/$relativePath"
                    $syncCount++
                } catch {
                    Write-Host "ERROR: Failed to copy backend/$relativePath : $($_.Exception.Message)"
                    $errorCount++
                }
            } else {
                Write-Host "SKIP: backend/$relativePath (no changes)"
            }
        }
    }

    # 清理孤立文件
    if (Test-Path -LiteralPath $targetDir) {
        $deployFiles = Get-ChildItem -LiteralPath $targetDir -Recurse -File | Where-Object {
            $_.FullName -notmatch '__pycache__' -and
            $_.FullName -notmatch '\.git' -and
            $_.FullName -notmatch '\.venv' -and
            $_.FullName -notmatch '\.pyc$'
        }

        foreach ($deployFile in $deployFiles) {
            $relativePath = $deployFile.FullName.Substring($targetDir.Length + 1)
            $sourceFile = Join-Path $sourceDir $relativePath

            if (-not (Test-Path -LiteralPath $sourceFile)) {
                if ($DryRun) {
                    Write-Host "DRYRUN: Would delete orphan: backend/$relativePath"
                } else {
                    try {
                        Remove-Item -LiteralPath $deployFile.FullName -Force
                        Write-Host "DELETE: Orphan: backend/$relativePath"
                    } catch {
                        Write-Host "ERROR: Failed to delete orphan backend/$relativePath : $($_.Exception.Message)"
                        $errorCount++
                    }
                }
            }
        }
    }
    Write-Host ""
}

# 同步配置文件
$configFiles = @(
    "docker-compose.yml",
    "Dockerfile",
    ".dockerignore",
    ".env.docker.example",
    "requirements.txt"
)

foreach ($cfgFile in $configFiles) {
    $src = Join-Path $ProjectRoot $cfgFile
    $dst = Join-Path $DockerDeployRoot $cfgFile

    if (-not (Test-Path -LiteralPath $src)) {
        continue
    }

    $totalFiles++

    if ($DryRun) {
        Write-Host "DRYRUN: Would copy: $cfgFile"
        $syncCount++
    } else {
        $shouldCopy = $true

        if ((Test-Path -LiteralPath $dst) -and -not $Force) {
            $sourceHash = (Get-FileHash -LiteralPath $src -Algorithm MD5).Hash
            $destHash = (Get-FileHash -LiteralPath $dst -Algorithm MD5).Hash
            if ($sourceHash -eq $destHash) {
                $shouldCopy = $false
                $skipCount++
            }
        }

        if ($shouldCopy) {
            try {
                Copy-Item -LiteralPath $src -Destination $dst -Force
                Write-Host "OK: $cfgFile"
                $syncCount++
            } catch {
                Write-Host "ERROR: Failed to copy $cfgFile : $($_.Exception.Message)"
                $errorCount++
            }
        } else {
            Write-Host "SKIP: $cfgFile (no changes)"
        }
    }
}

# docker-compose.yml -> docker-compose.yaml
$yamlSource = Join-Path $DockerDeployRoot "docker-compose.yml"
$yamlTarget = Join-Path $DockerDeployRoot "docker-compose.yaml"
if (Test-Path -LiteralPath $yamlSource) {
    if ($DryRun) {
        Write-Host "DRYRUN: Would copy: docker-compose.yml -> docker-compose.yaml"
    } else {
        $shouldCopy = $true
        if ((Test-Path -LiteralPath $yamlTarget) -and -not $Force) {
            $sourceHash = (Get-FileHash -LiteralPath $yamlSource -Algorithm MD5).Hash
            $destHash = (Get-FileHash -LiteralPath $yamlTarget -Algorithm MD5).Hash
            if ($sourceHash -eq $destHash) {
                $shouldCopy = $false
            }
        }
        if ($shouldCopy) {
            Copy-Item -LiteralPath $yamlSource -Destination $yamlTarget -Force
            Write-Host "OK: docker-compose.yml -> docker-compose.yaml"
            $totalFiles++
            $syncCount++
        } else {
            Write-Host "SKIP: docker-compose.yaml (no changes)"
        }
    }
}

Write-Host ""
Write-Host "========================================="
Write-Host "SUMMARY"
Write-Host "  Total Files: $totalFiles"
if ($DryRun) {
    Write-Host "  Would sync: $syncCount files"
} else {
    Write-Host "  Synced: $syncCount files"
    Write-Host "  Skipped: $skipCount files (no changes)"
    if ($errorCount -gt 0) {
        Write-Host "  Errors: $errorCount files"
    }
}
Write-Host "========================================="
Write-Host ""

if ($errorCount -gt 0) {
    exit 1
} else {
    exit 0
}
