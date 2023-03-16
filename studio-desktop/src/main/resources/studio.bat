@echo off


set VERSION=${project.build.finalName}
set STUDIO_CP=".\lib\*;%VERSION%.jar;."

java -cp "%STUDIO_CP%" -Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager studio.GUI
