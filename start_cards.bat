@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.

@REM All of the actual start logic lives in start_cards.py, shared between this
@REM wrapper (Windows) and start_cards.sh (Linux, macOS, WSL).
@REM Keep platform-specific logic in start_cards.py, not in the wrappers.

@echo off
setlocal

cd /d "%~dp0"

where /q py
if errorlevel 1 goto TryPython
@REM The launcher may be present without any Python 3 behind it; probe before delegating,
@REM so that we can still fall through to a standalone `python`
py -3 --version >nul 2>&1
if errorlevel 1 goto TryPython
py -3 start_cards.py %*
exit /b %errorlevel%

:TryPython
where /q python
if errorlevel 1 goto NoPython
python start_cards.py %*
exit /b %errorlevel%

:NoPython
echo Python 3 is required to start CARDS, but neither 'py' nor 'python' was found on the PATH. 1>&2
exit /b 1
