<#
.SYNOPSIS
    iTantra Developer CLI & Fast Deploy Tool
    Provides Flutter-like fluent interaction with ADB devices, fast reload, and API monitoring.

.EXAMPLE
    .\dev.ps1 run          # Build and install APK
    .\dev.ps1 fast         # Fast deploy / hot reload changed DEX (fastest install)
    .\dev.ps1 logs         # Monitor all iTantra AI, radio, and model APIs in real time
    .\dev.ps1 screenshot   # Capture and pull screenshot instantly
    .\dev.ps1 device       # Change or select connected device
#>

param (
    [string]$Action = ""
)

$configFile = "$PSScriptRoot\.device_id"

function Get-SelectedDevice {
    $devices = @(adb devices | Where-Object { $_ -match "\tdevice$" } | ForEach-Object { ($_ -split "\t")[0] })
    
    if ($devices.Count -eq 0) {
        Write-Host "No Android devices connected! Please connect a device via USB/Wi-Fi with USB debugging enabled." -ForegroundColor Red
        exit 1
    }

    if (Test-Path $configFile) {
        $saved = (Get-Content $configFile -Raw).Trim()
        if ($devices -contains $saved) {
            return $saved
        }
    }

    if ($devices.Count -eq 1) {
        $selected = $devices[0]
        $selected | Out-File -FilePath $configFile -Encoding utf8
        Write-Host "Auto-selected device: $selected" -ForegroundColor Green
        return $selected
    }

    Write-Host "`nConnected Devices:" -ForegroundColor Cyan
    for ($i = 0; $i -lt $devices.Count; $i++) {
        $model = (adb -s $devices[$i] shell getprop ro.product.model).Trim()
        Write-Host "  [$($i + 1)] $($devices[$i]) ($model)"
    }

    $choice = Read-Host "`nSelect device [1-$($devices.Count)] (Default: 1)"
    if ([string]::IsNullOrWhiteSpace($choice)) { $choice = 1 }
    $index = [int]$choice - 1
    $selected = $devices[$index]
    $selected | Out-File -FilePath $configFile -Encoding utf8
    Write-Host "Saved device: $selected" -ForegroundColor Green
    return $selected
}

function Invoke-Run($device) {
    Write-Host "`n=== Building & Installing iTantra on $device ===" -ForegroundColor Cyan
    & .\gradlew.bat installDebug
    if ($LASTEXITCODE -eq 0) {
        adb -s $device shell am start -n com.itantra.debug/com.itantra.MainActivity
        Write-Host "App launched on $device!" -ForegroundColor Green
    } else {
        Write-Host "Build failed." -ForegroundColor Red
    }
}

function Invoke-FastDeploy($device) {
    Write-Host "`n=== Fast Deploy / Hot Reload on $device ===" -ForegroundColor Cyan
    & .\gradlew.bat assembleDebug
    if ($LASTEXITCODE -eq 0) {
        $apk = "app\build\outputs\apk\debug\app-debug.apk"
        Write-Host "Pushing changed DEX diffs via FastDeploy..." -ForegroundColor Yellow
        adb -s $device install -r --fastdeploy $apk
        adb -s $device shell am start -n com.itantra.debug/com.itantra.MainActivity
        Write-Host "Hot swapped & restarted in seconds!" -ForegroundColor Green
    }
}

function Invoke-Logs($device) {
    Write-Host "`n=== Monitoring iTantra APIs & Core Modules on $device ===" -ForegroundColor Cyan
    Write-Host "(Press Ctrl+C to exit log monitoring)`n" -ForegroundColor Yellow
    adb -s $device logcat -v time -s "ModelDownloadManager:*" "MainViewModel:*" "ITantraService:*" "AudioCapture:*" "VADModule:*" "STTModule:*" "TTSModule:*" "WifiDirect:*" "BluetoothRFCOMM:*" "LanguageID:*"
}

function Invoke-Screenshot($device) {
    $out = "screenshot_$(Get-Date -Format 'yyyyMMdd_HHmmss').png"
    adb -s $device shell screencap -p /sdcard/it_screen.png
    adb -s $device pull /sdcard/it_screen.png $out
    adb -s $device shell rm /sdcard/it_screen.png
    Write-Host "Screenshot saved to $out" -ForegroundColor Green
}

function Invoke-Restart($device) {
    adb -s $device shell am force-stop com.itantra.debug
    adb -s $device shell am start -n com.itantra.debug/com.itantra.MainActivity
    Write-Host "App restarted on $device" -ForegroundColor Green
}

function Show-Menu {
    Clear-Host
    $device = Get-SelectedDevice
    $model = (adb -s $device shell getprop ro.product.model).Trim()
    
    Write-Host "==========================================" -ForegroundColor Cyan
    Write-Host "       iTantra Dev Control Center         " -ForegroundColor White
    Write-Host "  Target Device: $device ($model)         " -ForegroundColor Green
    Write-Host "==========================================" -ForegroundColor Cyan
    Write-Host "  [1] run        - Full Build & Install"
    Write-Host "  [2] fast       - Fast Deploy / Hot Swap (Fastest)"
    Write-Host "  [3] logs       - Monitor Live APIs & Neural Logs"
    Write-Host "  [4] screen     - Capture Device Screenshot"
    Write-Host "  [5] restart    - Restart App on Device"
    Write-Host "  [6] device     - Switch Target Device"
    Write-Host "  [7] clean      - Clean Gradle Build"
    Write-Host "  [0] exit       - Exit"
    Write-Host "------------------------------------------"
    $choice = Read-Host "Choose option"

    switch ($choice) {
        "1" { Invoke-Run $device }
        "2" { Invoke-FastDeploy $device }
        "3" { Invoke-Logs $device }
        "4" { Invoke-Screenshot $device }
        "5" { Invoke-Restart $device }
        "6" { 
            Remove-Item $configFile -ErrorAction SilentlyContinue
            $newDev = Get-SelectedDevice
            Write-Host "Switched to $newDev" -ForegroundColor Green
        }
        "7" { & .\gradlew.bat clean }
        default { exit }
    }
}

$targetDevice = Get-SelectedDevice

switch ($Action.ToLower()) {
    "run"        { Invoke-Run $targetDevice }
    "r"          { Invoke-Run $targetDevice }
    "fast"       { Invoke-FastDeploy $targetDevice }
    "f"          { Invoke-FastDeploy $targetDevice }
    "logs"       { Invoke-Logs $targetDevice }
    "l"          { Invoke-Logs $targetDevice }
    "screenshot" { Invoke-Screenshot $targetDevice }
    "s"          { Invoke-Screenshot $targetDevice }
    "restart"    { Invoke-Restart $targetDevice }
    "k"          { Invoke-Restart $targetDevice }
    "device"     {
        Remove-Item $configFile -ErrorAction SilentlyContinue
        $newDev = Get-SelectedDevice
    }
    "d"          {
        Remove-Item $configFile -ErrorAction SilentlyContinue
        $newDev = Get-SelectedDevice
    }
    default      { Show-Menu }
}
