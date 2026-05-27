#Requires -Version 5.1
$ErrorActionPreference = "Continue"
Write-Host "=== Interception driver check ===" -ForegroundColor Cyan

$dllPaths = @(
    "tools\interception\interception.dll",
    "D:\Desktop\Interception\library\x64\interception.dll"
)
foreach ($p in $dllPaths) {
    if (Test-Path $p) {
        $item = Get-Item $p
        Write-Host "[OK] DLL: $($item.FullName) ($($item.Length) bytes)" -ForegroundColor Green
    } else {
        Write-Host "[--] missing: $p" -ForegroundColor DarkGray
    }
}

$drivers = @("keyboard.sys", "mouse.sys")
Write-Host ""
Write-Host "--- System32\drivers ---" -ForegroundColor Cyan
foreach ($name in $drivers) {
    $path = Join-Path $env:SystemRoot "System32\drivers\$name"
    if (Test-Path $path) {
        Write-Host "[OK] $path" -ForegroundColor Green
    } else {
        Write-Host "[MISSING] $path" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "--- Device node \\.\interception00 ---" -ForegroundColor Cyan
try {
    $fs = New-Object System.IO.FileStream(
        "\\.\interception00",
        [System.IO.FileMode]::Open,
        [System.IO.FileAccess]::Read,
        [System.IO.FileShare]::ReadWrite
    )
    $fs.Close()
    Write-Host "[OK] Opened \\.\interception00 - driver is loaded." -ForegroundColor Green
    exit 0
} catch {
    Write-Host "[FAIL] Cannot open \\.\interception00 - $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""
Write-Host "=== Fix (same as create_context null in AutoAction) ===" -ForegroundColor Cyan
Write-Host "1. Close AutoAction and the game."
Write-Host "2. Open NORMAL PowerShell (not Run as administrator)."
Write-Host "   cd 'D:\Desktop\Interception\command line installer'"
Write-Host "   .\install-interception.exe /install"
Write-Host "   Accept UAC. Installer must NOT say 'Could not write'."
Write-Host "3. REBOOT the PC (required)."
Write-Host "4. Run this script again - expect [OK] for interception00."
Write-Host "5. If still fail: /uninstall, reboot, /install, reboot."
Write-Host "   Win11: disable Memory integrity under Core isolation."
exit 1
