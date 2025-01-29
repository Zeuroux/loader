@echo off
ECHO Building jar
call gradlew :minecraft:createFullJarRelease
ECHO Sanitize jar
unzip -q ./minecraft/build/intermediates/full_jar/release/createFullJarRelease/full.jar -d temp_jar && find temp_jar -type f -name "*.class" ! -name "NotificationListenerService.class" ! -name "Launcher.class" ! -name "GooglePlayStore.class" ! -name "AmazonAppStore.class" -delete && cd temp_jar && jar cf ../modified_full.jar * && cd .. && rm -rf temp_jar
ECHO Converting to dex
call d8 ".\minecraft\build\intermediates\full_jar\release\createFullJarRelease\full.jar"
ECHO Copying
copy "./classes.dex" "./app/src/main/assets/launcher.dex"
