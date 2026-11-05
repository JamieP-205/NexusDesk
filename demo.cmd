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
  rem Only the demo file is touched. The normal database, data\nexusdesk.mv.db, is left alone.
  if exist "data\nexusdesk-demo.mv.db" del "data\nexusdesk-demo.mv.db"
  if exist "data\nexusdesk-demo.trace.db" del "data\nexusdesk-demo.trace.db"
  echo Demo database reset.
)
echo Starting the NexusDesk demo. Open http://localhost:8080 once you see "Started NexusDeskApplication".
echo The first run also builds the fictional data; sign in after "Created fictional demo data" appears.
echo Press Ctrl+C to stop.
call "%~dp0mvnw.cmd" -q spring-boot:run "-Dspring-boot.run.profiles=demo"
