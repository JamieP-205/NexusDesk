@echo off
rem Starts the NexusDesk demo (fictional data, public demo passwords) on http://localhost:8080.
rem   demo.cmd          start the demo, keeping any demo changes from last time
rem   demo.cmd --reset  delete the demo database first, so it is rebuilt from scratch
setlocal
cd /d "%~dp0"
where java >nul 2>nul || (
  echo Java 17 or newer is needed. Install a JDK such as Eclipse Temurin 21, open a new terminal and try again.
  exit /b 1
)
if /i "%~1"=="--reset" (
  rem Only the demo file is touched. The normal database is data
exusdesk.mv.db and is left alone.
  if exist "data
exusdesk-demo.mv.db" del "data
exusdesk-demo.mv.db"
  if exist "data
exusdesk-demo.trace.db" del "data
exusdesk-demo.trace.db"
  echo Demo database reset.
)
echo Starting the NexusDesk demo. Open http://localhost:8080 when you see "Started NexusDeskApplication".
echo Press Ctrl+C to stop.
call mvnw.cmd -q spring-boot:run "-Dspring-boot.run.profiles=demo"
