@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@if "%DEBUG%" == "" @echo off
@rem ##########################################################################
@rem
@rem  risk_based_scheduler startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%..

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and RISK_BASED_SCHEDULER_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS=

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto execute

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\lib\risk_based_scheduler-1.0-SNAPSHOT.jar;%APP_HOME%\lib\mason.19.jar;%APP_HOME%\lib\j3dcore.jar;%APP_HOME%\lib\j3dutils.jar;%APP_HOME%\lib\portfolio.jar;%APP_HOME%\lib\vecmath.jar;%APP_HOME%\lib\gson-2.9.0.jar;%APP_HOME%\lib\guava-31.1-jre.jar;%APP_HOME%\lib\javalin-3.13.7.jar;%APP_HOME%\lib\jfreechart-1.0.17.jar;%APP_HOME%\lib\jcommon-1.0.21.jar;%APP_HOME%\lib\commons-lang3-3.0.jar;%APP_HOME%\lib\commons-math3-3.6.1.jar;%APP_HOME%\lib\commons-csv-1.4.jar;%APP_HOME%\lib\commons-cli-1.5.0.jar;%APP_HOME%\lib\commons-io-2.6.jar;%APP_HOME%\lib\itextpdf-5.5.13.2.jar;%APP_HOME%\lib\jmf-2.1.1e.jar;%APP_HOME%\lib\smile-core-2.6.0.jar;%APP_HOME%\lib\failureaccess-1.0.1.jar;%APP_HOME%\lib\listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar;%APP_HOME%\lib\jsr305-3.0.2.jar;%APP_HOME%\lib\checker-qual-3.12.0.jar;%APP_HOME%\lib\error_prone_annotations-2.11.0.jar;%APP_HOME%\lib\j2objc-annotations-1.3.jar;%APP_HOME%\lib\slf4j-reload4j-1.7.36.jar;%APP_HOME%\lib\smile-data-2.6.0.jar;%APP_HOME%\lib\smile-graph-2.6.0.jar;%APP_HOME%\lib\smile-math-2.6.0.jar;%APP_HOME%\lib\slf4j-api-1.7.36.jar;%APP_HOME%\lib\jetty-webapp-9.4.40.v20210413.jar;%APP_HOME%\lib\websocket-server-9.4.40.v20210413.jar;%APP_HOME%\lib\jetty-servlet-9.4.40.v20210413.jar;%APP_HOME%\lib\jetty-security-9.4.40.v20210413.jar;%APP_HOME%\lib\jetty-server-9.4.40.v20210413.jar;%APP_HOME%\lib\kotlin-stdlib-jdk8-1.3.71.jar;%APP_HOME%\lib\xml-apis-1.3.04.jar;%APP_HOME%\lib\websocket-servlet-9.4.40.v20210413.jar;%APP_HOME%\lib\javax.servlet-api-3.1.0.jar;%APP_HOME%\lib\websocket-client-9.4.40.v20210413.jar;%APP_HOME%\lib\jetty-client-9.4.40.v20210413.jar;%APP_HOME%\lib\jetty-http-9.4.40.v20210413.jar;%APP_HOME%\lib\websocket-common-9.4.40.v20210413.jar;%APP_HOME%\lib\jetty-io-9.4.40.v20210413.jar;%APP_HOME%\lib\jetty-xml-9.4.40.v20210413.jar;%APP_HOME%\lib\kotlin-stdlib-jdk7-1.3.71.jar;%APP_HOME%\lib\kotlin-stdlib-1.3.71.jar;%APP_HOME%\lib\reload4j-1.2.19.jar;%APP_HOME%\lib\jetty-util-ajax-9.4.40.v20210413.jar;%APP_HOME%\lib\jetty-util-9.4.40.v20210413.jar;%APP_HOME%\lib\websocket-api-9.4.40.v20210413.jar;%APP_HOME%\lib\kotlin-stdlib-common-1.3.71.jar;%APP_HOME%\lib\annotations-13.0.jar


@rem Execute risk_based_scheduler
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %RISK_BASED_SCHEDULER_OPTS%  -classpath "%CLASSPATH%" org.mitre.bch.cath.simulation.model.CathLabSim %*

:end
@rem End local scope for the variables with windows NT shell
if "%ERRORLEVEL%"=="0" goto mainEnd

:fail
rem Set variable RISK_BASED_SCHEDULER_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
if  not "" == "%RISK_BASED_SCHEDULER_EXIT_CONSOLE%" exit 1
exit /b 1

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
