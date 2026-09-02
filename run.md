# iTantra — Run Commands

## Build & Install
```
.\gradlew.bat installDebug
```

## Launch App on Device
```
adb -s 3968851956ZZZZZ shell am start -n com.itantra.debug/com.itantra.MainActivity
```

## Kill App
```
adb -s 3968851956ZZZZZ shell am force-stop com.itantra.debug
```

## View Logs (All iTantra Modules)
```
adb -s 3968851956ZZZZZ logcat -v time -s "ITantraService:*" "AudioCapture:*" "VADModule:*" "STTModule:*" "TTSModule:*" "WifiDirect:*" "BluetoothRFCOMM:*" "ModelDownload:*" "LanguageID:*"
```

## APK Checksum (Debug Build — v2.0.0 Production)
```
app\build\outputs\apk\debug\app-debug.apk
Size:    63.40 MB (66,476,992 bytes)
SHA-256: D0136ABD4B6AF6D78BE017267AE45BB21D0C4B0F09338B9D907DF1105CD34FFC
SHA-1:   3A42656CBA811E78EAF85B13D70A5362117F2C31
MD5:     2A855F6D548B9ACD86238A0DD741D2CD
```

## Capture Screenshot
```
adb -s 3968851956ZZZZZ shell screencap -p /sdcard/screen.png
adb -s 3968851956ZZZZZ pull /sdcard/screen.png .
```

## ADB Device Check
```
adb devices
```

## Clean Build
```
.\gradlew.bat clean assembleDebug
```