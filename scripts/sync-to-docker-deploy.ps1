# Sync Script - Local to docker-deploy
# 同步后端部署文件到 docker-deploy 目录（快麦取货通 - FastAPI 后端）

param(
    [switch]$Force,
    [switch]$DryRun
)

$ErrorActionPreference = "Continue"

$ProjectRoot = Split-Path $PSScriptRoot -Parent
$DockerDeployRoot = Join-Path $ProjectRoot "docker-deploy"

Write-Host "========================================="
Write-Host "  Sync Script - Local to docker-deploy"
Write-Host "  快麦取货通 - FastAPI 后端"
Write-Host "========================================="
Write-Host ""

if (-not (Test-Path -LiteralPath $DockerDeployRoot)) {
    Write-Host "[INFO] docker-deploy 目录不存在，自动创建: $DockerDeployRoot"
    New-Item -ItemType Directory -Path $DockerDeployRoot -Force | Out-Null
}

Write-Host "[INFO] Project Root: $ProjectRoot"
Write-Host "[INFO] Docker Deploy: $DockerDeployRoot"
Write-Host ""

$syncItems = @()

# 后端源代码目录
$syncItems += @{
    Source = "backend"
    Target = "backend"
    Description = "FastAPI 后端源代码"
    Type = "directory"
}

# 配置文件
$configFiles = @(
    "docker-compose.yml",
    "Dockerfile",
    ".dockerignore",
    ".env.docker.example",
    "requirements.txt"
)

foreach ($file in $configFiles) {
    $syncItems += @{
        Source = $file
        Target = $file
        Description = "Config: $file"
        Type = "file"
    }
}

$totalFiles = 0
$syncCount = 0
$skipCount = 0
$deleteCount = 0
$errorCount = 0

foreach ($item in $syncItems) {
    $sourcePath = Join-Path $ProjectRoot $item.Source
    $targetPath = Join-Path $DockerDeployRoot $item.Target

    Write-Host "-----------------------------------------"
    Write-Host "[INFO] Syncing: $($item.Description)"
    Write-Host "[INFO] From: $sourcePath"
    Write-Host "[INFO] To: $targetPath"
    Write-Host ""

    if (-not (Test-Path -LiteralPath $sourcePath)) {
        Write-Host "[SKIP] Source not found: $($item.Source)"
        continue
    }

    if ($item.Type -eq "directory") {
        $files = Get-ChildItem -LiteralPath $sourcePath -Recurse -File | Where-Object {
            $_.FullName -notmatch '__pycache__' -and
            $_.FullName -notmatch '\.git' -and
            $_.FullName -notmatch '\.venv' -and
            $_.FullName -notmatch 'node_modules' -and
            $_.FullName -notmatch '\.pyc$'
        }

        $totalFiles += $files.Count

        foreach ($file in $files) {
            $relativePath = $file.FullName.Substring($sourcePath.Length + 1)
            $targetFile = Join-Path $targetPath $relativePath
            $targetDir = Split-Path $targetFile -Parent

            if (-not (Test-Path -LiteralPath $targetDir)) {
                if (-not $DryRun) {
                    New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
                }
            }

            if ($DryRun) {
                Write-Host "[DRYRUN] Would copy: $($item.Source)/$relativePath"
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
                        Write-Host "[OK] $($item.Source)/$relativePath"
                        $syncCount++
                    } catch {
                        Write-Host "[ERROR] Failed to copy $($item.Source)/$relativePath : $($_.Exception.Message)"
                        $errorCount++
                    }
                } else {
                    Write-Host "[SKIP] $($item.Source)/$relativePath (no changes)"
                }
            }
        }

        # 清理 docker-deploy 中存在但主项目中不存在的文件（孤立文件）
        if (Test-Path -LiteralPath $targetPath) {
            $deployFiles = Get-ChildItem -LiteralPath $targetPath -Recurse -File | Where-Object {
                $_.FullName -notmatch '__pycache__' -and
                $_.FullName -notmatch '\.git' -and
                $_.FullName -notmatch '\.venv' -and
                $_.FullName -notmatch '\.pyc$'
            }

            foreach ($deployFile in $deployFiles) {
                $relativePath = $deployFile.FullName.Substring($targetPath.Length + 1)
                $sourceFile = Join-Path $sourcePath $relativePath

                if (-not (Test-Path -LiteralPath $sourceFile)) {
                    if ($DryRun) {
                        Write-Host "[DRYRUN] Would delete orphan: $($item.Target)/$relativePath"
                        $deleteCount++
                    } else {
                        try {
                            Remove-Item -LiteralPath $deployFile.FullName -Force
                            Write-Host "[DELETE] Orphan: $($item.Target)/$relativePath"
                            $deleteCount++
                        } catch {
                            Write-Host "[ERROR] Failed to delete orphan $($item.Target)/$relativePath : $($_.Exception.Message)"
                            $errorCount++
                        }
                    }
                }
            }

            # 清理孤立文件后产生的空目录
            if (-not $DryRun) {
                $emptyDirs = Get-ChildItem -LiteralPath $targetPath -Recurse -Directory | Sort-Object -Property { $_.FullName.Length } -Descending
                foreach ($dir in $emptyDirs) {
                    $remainingItems = @(Get-ChildItem -LiteralPath $dir.FullName -Force -ErrorAction SilentlyContinue)
                    if ($remainingItems.Count -eq 0) {
                        try {
                            Remove-Item -LiteralPath $dir.FullName -Force
                            Write-Host "[RMDIR] Empty: $($item.Target)/$($dir.FullName.Substring($targetPath.Length + 1))"
                        } catch {
                            Write-Host "[ERROR] Failed to remove empty dir: $($_.Exception.Message)"
                        }
                    }
                }
            }
        }
    } else {
        $totalFiles++

        if ($DryRun) {
            Write-Host "[DRYRUN] Would copy: $($item.Source)"
            $syncCount++
        } else {
            $shouldCopy = $true

            if ((Test-Path -LiteralPath $targetPath) -and -not $Force) {
                $sourceHash = (Get-FileHash -LiteralPath $sourcePath -Algorithm MD5).Hash
                $destHash = (Get-FileHash -LiteralPath $targetPath -Algorithm MD5).Hash

                if ($sourceHash -eq $destHash) {
                    $shouldCopy = $false
                    $skipCount++
                }
            }

            if ($shouldCopy) {
                try {
                    Copy-Item -LiteralPath $sourcePath -Destination $targetPath -Force
                    Write-Host "[OK] $($item.Source)"
                    $syncCount++
                } catch {
                    Write-Host "[ERROR] Failed to copy $($item.Source) : $($_.Exception.Message)"
                    $errorCount++
                }
            } else {
                Write-Host "[SKIP] $($item.Source) (no changes)"
            }
        }
    }

    Write-Host ""
}

# 额外步骤：docker-compose.yml → docker-compose.yaml（绿联 NAS Docker GUI 只识别 .yaml 扩展名）
$yamlSource = Join-Path $DockerDeployRoot "docker-compose.yml"
$yamlTarget = Join-Path $DockerDeployRoot "docker-compose.yaml"
if (Test-Path -LiteralPath $yamlSource) {
    if ($DryRun) {
        Write-Host "[DRYRUN] Would copy: docker-compose.yml -> docker-compose.yaml"
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
            Write-Host "[OK] docker-compose.yml -> docker-compose.yaml"
            $totalFiles++
            $syncCount++
        } else {
            Write-Host "[SKIP] docker-compose.yaml (no changes)"
        }
    }
}

Write-Host "========================================="
Write-Host "[SUMMARY]"
Write-Host "  Total Files: $totalFiles"
if ($DryRun) {
    Write-Host "  Would sync: $syncCount files"
    if ($deleteCount -gt 0) {
        Write-Host "  Would delete: $deleteCount orphan files"
    }
} else {
    Write-Host "  Synced: $syncCount files"
    Write-Host "  Skipped: $skipCount files (no changes)"
    if ($deleteCount -gt 0) {
        Write-Host "  Deleted: $deleteCount orphan files"
    }
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
