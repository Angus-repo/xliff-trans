@echo off
cd /d "%~dp0"
for %%f in (xliff-trans-*.jar) do java -cp . -jar "%%f"
