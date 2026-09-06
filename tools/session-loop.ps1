# Запуск цепочки сессий из PowerShell — обёртка над tools/session-loop.sh.
#
# ПРЕДМЕТ. Сценарий цикла написан на bash и рассчитан на bash из поставки
# Git. Из PowerShell на этой машине `bash` разрешается в WSL (каталог
# WindowsApps), а `claude.exe` внутри WSL не виден: цикл упал бы предполётной
# проверкой «claude не найден на PATH» — то есть отказом, который о настоящей
# причине не говорит. Обёртка выбирает bash из Git ЯВНО и отказывается
# запускаться через WSL: молчаливой подмены интерпретатора нет.
#
# Запуск (из любого места):
#   .../tools/session-loop.ps1 5
#   .../tools/session-loop.ps1 5 --dry-run
#
# Аргументы уезжают в сценарий как есть; код возврата возвращается как есть
# (его значения — в шапке tools/session-loop.sh и в
# .claude/skills/session-chain.md). Переменные среды (SESSION_MAX_USD,
# SESSION_TIMEOUT, ...) наследует процесс bash, задавать их можно по-обычному:
# $env:SESSION_MAX_USD = '5'.
#
# Путь к bash перекрывается $env:SESSION_GIT_BASH.

$ErrorActionPreference = 'Stop'

function Find-GitBash {
    $candidates = @()
    if ($env:SESSION_GIT_BASH) { $candidates += $env:SESSION_GIT_BASH }
    if ($env:ProgramFiles) { $candidates += (Join-Path $env:ProgramFiles 'Git/bin/bash.exe') }
    if (${env:ProgramFiles(x86)}) { $candidates += (Join-Path ${env:ProgramFiles(x86)} 'Git/bin/bash.exe') }
    if ($env:LOCALAPPDATA) { $candidates += (Join-Path $env:LOCALAPPDATA 'Programs/Git/bin/bash.exe') }

    # Последний кандидат выводится из места самого git: поставка бывает и не
    # там, где её ждут, а `git.exe` на PATH есть у всякого, кто этим
    # репозиторием пользуется. Git/cmd/git.exe -> Git/bin/bash.exe.
    $git = Get-Command git.exe -ErrorAction SilentlyContinue
    if ($git) {
        $gitHome = Split-Path -Parent (Split-Path -Parent $git.Source)
        $candidates += (Join-Path $gitHome 'bin/bash.exe')
    }

    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path -LiteralPath $candidate)) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }
    return $null
}

$bash = Find-GitBash

if (-not $bash) {
    Write-Host 'ОТКАЗ: bash из поставки Git не найден.' -ForegroundColor Red
    Write-Host 'Ожидались: $env:SESSION_GIT_BASH, Git/bin/bash.exe в %ProgramFiles%, каталог рядом с git.exe.'
    Write-Host 'Поставить Git для Windows либо назвать путь: $env:SESSION_GIT_BASH = "...Git/bin/bash.exe"'
    exit 2
}

if ($bash -like '*WindowsApps*') {
    Write-Host "ОТКАЗ: $bash — это запуск WSL, а не bash из поставки Git." -ForegroundColor Red
    Write-Host 'В WSL не виден claude.exe, и цикл упал бы предполётной проверкой не по своей причине.'
    exit 2
}

$root = Split-Path -Parent $PSScriptRoot

Write-Host "bash: $bash"
Write-Host "корень: $root"

Push-Location -LiteralPath $root
try {
    & $bash 'tools/session-loop.sh' @args
    $code = $LASTEXITCODE
}
finally {
    Pop-Location
}

exit $code
