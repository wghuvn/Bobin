@echo off
setlocal
cd /d "%~dp0"
echo Deleting old AK-47 sources and resources from THIS project folder.
rmdir /s /q "art\ak47" 2>nul
rmdir /s /q "docs\ak47-b2" 2>nul
rmdir /s /q "docs\ak47-b2.1" 2>nul
rmdir /s /q "tools\ak47" 2>nul
rmdir /s /q "src\main\java\com\bobincraft\weapon\ak47" 2>nul
rmdir /s /q "src\test\java\com\bobincraft\weapon\ak47" 2>nul
rmdir /s /q "src\main\resources\assets\bobin_craft\weapons\ak47" 2>nul
rmdir /s /q "src\main\resources\assets\bobin_craft\textures\item\ak47" 2>nul
del /f /q "src\main\resources\assets\bobin_craft\sounds\weapon\ak47*.ogg" 2>nul
del /f /q "src\main\resources\assets\bobin_craft\models\item\ak47*.json" 2>nul
del /q "src\main\java\com\bobincraft\weapon\AK47Item.java" 2>nul
del /q "src\main\resources\assets\bobin_craft\textures\item\ak47.png" 2>nul
del /q "src\main\resources\data\bobin_craft\recipes\ak47.json" 2>nul
del /q "AK47_PHASE_B2.md" "AK47_SCALE_FIX.md" "README_AK47_B2.txt" 2>nul
del /q "docs\BOBIN_CRAFT_WEAPON_TARGET_IMPLEMENTATION_REPORT_RU.md" 2>nul
del /q "SOURCE_SHA256.json" 2>nul
echo Cleanup finished. Now run gradlew.bat build -x test
