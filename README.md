Arquitectura y Archivos del Módulo de Onboarding
Hoy hemos construido un módulo completo siguiendo los estándares Clean Architecture y MVVM (Model-View-ViewModel) recomendados por Google. Esta es la radiografía exacta de tu proyecto ahora mismo:

1. Capa de Presentación (UI y Navegación)

HybridApplication.kt & MainActivity.kt: Los puntos de entrada. Configuran la inyección de dependencias (Hilt) para toda la app y lanzan el sistema de navegación.

Screen.kt & NavGraph.kt: El mapa de carreteras de la app. Definen las rutas (Auth, Onboarding, Home) y las reglas de transición entre pantallas.

OnboardingScreen.kt & LoadingScreen.kt: Las vistas puras en Jetpack Compose. Solo se encargan de pintar botones, tarjetas, animaciones y reaccionar a los toques del usuario. No procesan datos.

2. Capa de Lógica de Negocio (State Management)

OnboardingViewModel.kt: El cerebro de la vista. Mantiene el estado (OnboardingState), valida que el usuario sea mayor de 16 años, gestiona en qué paso del formulario estamos y transforma los datos crudos de la interfaz visual en un formato que el servidor pueda entender.

3. Capa de Datos y Red (Clean Architecture)

ProfileUpdateRequest.kt (DTO): Un objeto de transferencia de datos. Modela exactamente la estructura JSON (age, weight, injuries) que tu base de datos de MongoDB espera recibir.

UserApi.kt: La interfaz de Retrofit. Define la ruta HTTP exacta (PATCH /api/users/profile) a la que Android debe enviar los datos.

UserRepository.kt & UserRepositoryImpl.kt: El Repositorio. Actúa como intermediario. El ViewModel le pide al repositorio "actualiza el perfil", y el repositorio se encarga de hablar con Retrofit, manejar las respuestas HTTP (200 OK, 400 Error) y devolver un resultado seguro.

NetworkModule.kt: El módulo de inyección de Hilt. Le enseña a la app cómo construir Retrofit por detrás para que tú no tengas que instanciarlo manualmente en cada pantalla.
