# PeekerGuard

An Android app that notifies the user if someone else is looking at the screen, based on prior provided architecture.

## Overview

PeekerGuard is an innovative Android application designed to protect user privacy by detecting when someone else is looking at their device screen. Using advanced computer vision and machine learning techniques, the app provides real-time notifications to alert users of potential privacy breaches.

## Features

- **Real-time Detection**: Continuously monitors for additional faces or eyes looking at the screen
- **Privacy Notifications**: Instant alerts when unauthorized viewing is detected
- **Customizable Sensitivity**: Adjustable detection thresholds for different environments
- **Battery Optimized**: Efficient algorithms to minimize battery drain
- **Privacy-First Design**: All processing happens locally on device

## Architecture

The app follows a modular architecture based on previously provided specifications:

### Core Components

1. **Camera Module**: Handles front-facing camera access and image capture
2. **Detection Engine**: Computer vision pipeline for face/eye detection
3. **Notification Service**: Background service for real-time alerts
4. **Settings Manager**: User preferences and configuration management
5. **Privacy Controller**: Ensures data privacy and local processing

### Technology Stack

- **Language**: Kotlin/Java
- **Framework**: Android SDK
- **Computer Vision**: OpenCV or ML Kit
- **Architecture Pattern**: MVVM with Repository pattern
- **Database**: Room for local storage
- **Background Processing**: WorkManager for efficient task scheduling

## Installation

_Coming soon - App is currently in development_

## Usage

1. Install the app and grant camera permissions
2. Configure detection sensitivity in settings
3. Enable background monitoring
4. Receive notifications when unauthorized viewing is detected

## Privacy & Security

- **No Data Collection**: All processing happens locally on your device
- **No Network Access**: The app works completely offline
- **Secure Storage**: Sensitive settings encrypted locally
- **Open Source**: Full transparency with open source codebase

## Contributing

Contributions are welcome! Please read our contributing guidelines and submit pull requests for any improvements.

## License

_License information to be added_

## Development Status

🚧 **Currently in Development** 🚧

This repository contains the initial project setup. Development is ongoing based on the provided architecture specifications.
