@echo off
python "%~dp0scripts\publish.py" %*
exit /b %ERRORLEVEL%
