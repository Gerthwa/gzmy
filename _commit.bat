@echo off
cd /d C:\Users\burak\OneDrive\MASAST~1\gzmy\gzmy
set GIT_AUTHOR_NAME=Gerthwa
set GIT_AUTHOR_EMAIL=gerthwa@users.noreply.github.com
set GIT_COMMITTER_NAME=Gerthwa
set GIT_COMMITTER_EMAIL=gerthwa@users.noreply.github.com
git add -A
git commit -m "fix: notifications not delivered when app killed - switch to hybrid FCM payload"
git push origin main
del "%~f0"
