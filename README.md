# WeatherX

A native Android weather app project built with Kotlin and Jetpack Compose.

## Current implementation
- Persian right-to-left interface.
- Search for cities using the Open-Meteo Geocoding API.
- Add cities to a saved locations list.
- Select a saved location.
- Remove saved locations.
- Persist the list locally with SharedPreferences.
- Starts with Rahimabad and Nashtarud.
- Android 9 (API 28) and newer.

## Build
GitHub Actions builds a debug APK on every push to `main` and when manually dispatched. Download the `WeatherX-debug` artifact from the Actions run.

## Data provider
City search uses the Open-Meteo Geocoding API. Weather forecast integration is the next development step.
