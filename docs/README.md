# Honours Project - Fog of Earth (Working Title)

A map-based Android application that uses a "fog-of-war" mechanic to visualise explored
areas as the user moves around the real world. The app updates exploration
progress using location updates as well as shared progress from other users, 
with all progress persisting locally.

## Features

- **Fog-Of-War exploration**: reveals the map around the user as they move
- **Background location tracking**: continues updating exploration while minimised (utilising a foreground service)
- **Local persistence**: saves and restores explored areas between sessions
- **Share/Import progress (QR)**: export explored points as a QR code and import via scanner

## Tech Stack

### Languages

- **Java** - core application logic
- **JSON** - local data storage format
- **XML** - Android UI layouts and configuration

### Frameworks & Libraries

- **Android SDK**
- **OpenStreetMap / osmdroid** 
- **ZXing / JourneyApps Barcode Scanner**

### Tools

- **Android Studio**
- **Github**

## License
This project is licensed under the MIT License — see the `LICENSE` file for details.


Icons made by Pixel perfect from www.flaticon.com
