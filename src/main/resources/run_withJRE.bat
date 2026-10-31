@echo off
cd /d "%~dp0"
for %%f in (xliff-trans-*.jar) do "jre\bin\java.exe" -cp . -jar "%%f"
