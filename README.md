# Location-Based Diary System

## Overview
This project implements a location-based reminder system on Android.  
It allows users to create reminders associated with meaningful places, which are triggered automatically when entering a defined geographic area.

The system extends traditional time-based reminders by incorporating spatial context and semantic place information.

---

## Features
- Create and store location-based reminders
- Geofence-based triggering using Google Play Services
- Map-based interaction for selecting and visualising locations
- POI (Point of Interest) search based on user intent or category
- Local persistence using Room database

---

## System Architecture
The system consists of three main components:

- **Android client (Kotlin + Jetpack Compose)**  
  Handles user interaction, local data storage, and geofence registration.

- **Google Play Services**  
  Provides location tracking (Fused Location Provider) and geofencing functionality.

- **Backend service (Node.js + PostGIS)**  
  Performs spatial queries to retrieve nearby Points of Interest (POIs).

---

## Technologies Used
- Kotlin + Jetpack Compose
- Google Play Services (Geofencing API, Fused Location Provider)
- Room (local database)
- Node.js + Express
- PostgreSQL + PostGIS

---

## Notes on Implementation

### Google Maps Integration
The application uses the Google Maps API for map visualisation.

Due to API key restrictions and device-specific configurations, map tiles may not load correctly on some devices or emulators.  
This behaviour is related to Google Play Services availability and API key authorisation settings rather than the application logic itself.

---

### POI Data and Backend
The POI search functionality relies on a backend service connected to a PostGIS-enabled database.

Access to the dataset may require specific network or institutional access, and therefore the full data pipeline may not be directly reproducible in all environments.

---

## Limitations
- Geofence triggering may experience slight delays depending on device location update frequency
- Map rendering depends on Google Play Services availability
- Backend POI data access is environment-dependent

---

## Project Context
This project was developed as part of an undergraduate dissertation.  
The repository contains the final implementation of the system.
