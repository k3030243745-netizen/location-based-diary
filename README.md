# Location-Based Diary System

## Overview
This project implements a location-based reminder system on Android, designed as part of an undergraduate dissertation.

Unlike traditional time-based reminders, the system associates user tasks with meaningful places. Reminders are triggered automatically when the user enters a defined geographic area, combining spatial context with semantic place understanding.

---

## Features
- Creation and management of location-based reminders  
- Geofence-based triggering using Google Play Services  
- Map-based interaction for selecting and visualising locations  
- POI (Point of Interest) search based on user intent or category  
- Local data persistence using Room database  

---

## System Architecture
The system follows a layered architecture consisting of three main components:

- **Android client (Kotlin + Jetpack Compose)**  
  Handles user interaction, local data storage, and geofence registration.

- **Google Play Services**  
  Provides device location (Fused Location Provider) and geofencing capabilities.

- **Backend service (Node.js + PostGIS)**  
  Performs spatial queries to retrieve nearby Points of Interest (POIs).

---

## Project Structure
- `app/` – Android client application  
- `backend/` – Node.js backend service for POI queries and spatial processing  

---

## Technologies Used
- Kotlin + Jetpack Compose  
- Google Play Services (Geofencing API, Fused Location Provider)  
- Room (local database)  
- Node.js + Express  
- PostgreSQL + PostGIS  

---

## Backend and Data Access
The backend service is implemented using Node.js and Express and is located in the `backend/` directory. It is responsible for handling spatial queries and retrieving nearby POIs from a PostGIS-enabled database.

### Database Configuration
Database connection details are managed via environment variables. Sensitive information such as credentials is not included in this repository.

To run the backend locally, a `.env` file should be created, for example:
DB_HOST=your_host
DB_PORT=5432
DB_NAME=your_database
DB_USER=your_username
DB_PASSWORD=your_password


---

## Implementation Notes

### Google Maps Integration
The application uses the Google Maps API for map visualisation.

Due to API key restrictions and device-specific configurations, map tiles may not load correctly on some devices or emulators. This is related to Google Play Services availability and API key authorisation rather than application logic.

---

### POI Data and Reproducibility
The POI search functionality relies on a backend service connected to a PostGIS database.

As the dataset is accessed through an institutional environment, full reproduction of the backend functionality may require appropriate access permissions and network configuration.

This reflects typical real-world system constraints where data access is restricted.

---

## Limitations
- Geofence triggering may experience slight delays depending on device location update frequency  
- Map rendering depends on Google Play Services availability  
- Backend POI data access is environment-dependent  

---

## Project Context
This repository contains the final implementation developed for an undergraduate dissertation, demonstrating the integration of mobile development, spatial data processing, and context-aware computing.
