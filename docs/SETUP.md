# Setup (macOS)

## Required Software

- Xcode Command Line Tools
- Java 17 JDK

## Install Steps

### 1) Install Xcode Command Line Tools

```bash
xcode-select --install
```

### 2) Install Homebrew (if needed)

See [https://brew.sh](https://brew.sh)

### 3) Install Java 17

```bash
brew install --cask temurin@17
```

### 4) Verify Installation

```bash
java -version
```

Expected:

- Java reports version 17.x
- Gradle wrapper will download a compatible Gradle version automatically.

## Optional (future Android app module)

Android app module now exists. To build/run Android targets locally, also install:

- Install Android Studio (latest stable)
- Install Android SDK Platform for API 29+
- Install Android SDK Build-Tools and platform-tools

Then set your SDK path in repo root `local.properties`:

```properties
sdk.dir=/Users/<you>/Library/Android/sdk
```

Package/app ID planned for scaffolding:

- `com.tweakalizer.tablet`
