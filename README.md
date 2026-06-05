# Hybrid.Ai Training - Android Client

Android native client for the Hybrid.Ai Training platform. This application combines strength and endurance training (running) with a generative AI engine (Google Gemini) to provide adaptive workout plans.

Built with a modern Android stack, it follows **Clean Architecture** principles, **MVVM** pattern, and a fully declarative UI with **Jetpack Compose**.

## Tech Stack

* **Language:** Kotlin
* **UI Toolkit:** Jetpack Compose (Material Design 3)
* **Architecture:** Clean Architecture + MVVM (Package by Feature)
* **Asynchrony:** Coroutines & StateFlow
* **Networking:** Retrofit2 + OkHttp
* **Local Persistence:** Room Database (Offline-First approach)
* **Dependency Injection:** Manual DI / Provider Modules
* **Authentication:** Google Sign-In (OAuth 2.0)
* **Tracking:** Google Maps SDK & Location Services

##Architecture & Structure

The project is structured following a **Package by Feature** approach within the `app/src/main/java` directory to ensure high cohesion and low coupling:

* `core/`: Transversal infrastructure (Room, Retrofit, DI modules).
* `auth/`: Google Sign-In and local credential management.
* `onboarding/`: Initial biological and goal parameters setup.
* `home/`: Workouts dashboard and execution screens.
* `coach/`: AI conversational agent integration.
* `tracking/`: Background GPS and location services.
* `settings/`: App preferences (Dark Mode, i18n, Premium Paywall).

##Getting Started

### Prerequisites

* **Android Studio:** Iguana (2023.2.1) or newer.
* **Java:** JDK 17.
* **Minimum SDK:** API 26 (Android 8.0 Oreo).
