.PHONY: help build run tests
.DEFAULT_GOAL := help

GRADLEW ?= ./gradlew
GRADLE_ARGS ?= --no-daemon

APP_ID ?= com.example.currencyconverter1
MAIN_ACTIVITY ?= .MainActivity

SDK_DIR ?= $(shell awk -F= '/^sdk.dir=/{print $$2}' local.properties 2>/dev/null)
ADB ?= $(shell \
  if command -v adb >/dev/null 2>&1; then command -v adb; \
  elif [ -n "$$ANDROID_SDK_ROOT" ] && [ -x "$$ANDROID_SDK_ROOT/platform-tools/adb" ]; then echo "$$ANDROID_SDK_ROOT/platform-tools/adb"; \
  elif [ -n "$$ANDROID_HOME" ] && [ -x "$$ANDROID_HOME/platform-tools/adb" ]; then echo "$$ANDROID_HOME/platform-tools/adb"; \
  elif [ -n "$(SDK_DIR)" ] && [ -x "$(SDK_DIR)/platform-tools/adb" ]; then echo "$(SDK_DIR)/platform-tools/adb"; \
  fi)

help:
	@echo "make build   # build debug APK"
	@echo "make run     # install & run on device/emulator"
	@echo "make tests   # run unit tests"

build:
	@major="$$(java -version 2>&1 | awk -F'[\".]' '/version/ {m=$$2; if (m==1) m=$$3; print m; exit}')" ; \
	if [ -z "$$major" ]; then echo "ERROR: Java not found (set JAVA_HOME)"; exit 1; fi ; \
	if [ "$$major" -lt 17 ]; then echo "ERROR: Java $$major detected, but AGP 8.x requires Java 17+"; exit 1; fi
	@$(GRADLEW) $(GRADLE_ARGS) :app:assembleDebug

tests:
	@major="$$(java -version 2>&1 | awk -F'[\".]' '/version/ {m=$$2; if (m==1) m=$$3; print m; exit}')" ; \
	if [ -z "$$major" ]; then echo "ERROR: Java not found (set JAVA_HOME)"; exit 1; fi ; \
	if [ "$$major" -lt 17 ]; then echo "ERROR: Java $$major detected, but AGP 8.x requires Java 17+"; exit 1; fi
	@$(GRADLEW) $(GRADLE_ARGS) :app:testDebugUnitTest

run:
	@major="$$(java -version 2>&1 | awk -F'[\".]' '/version/ {m=$$2; if (m==1) m=$$3; print m; exit}')" ; \
	if [ -z "$$major" ]; then echo "ERROR: Java not found (set JAVA_HOME)"; exit 1; fi ; \
	if [ "$$major" -lt 17 ]; then echo "ERROR: Java $$major detected, but AGP 8.x requires Java 17+"; exit 1; fi
	@$(GRADLEW) $(GRADLE_ARGS) :app:installDebug
	@if [ -z "$(ADB)" ]; then echo "ERROR: adb not found (install platform-tools or set ANDROID_SDK_ROOT)"; exit 1; fi
	@$(ADB) shell am start -n "$(APP_ID)/$(MAIN_ACTIVITY)"
