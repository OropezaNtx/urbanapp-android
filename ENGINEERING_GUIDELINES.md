# Urban Platform - Engineering Guidelines

This document defines the permanent engineering standards and best practices for the Urban Platform project. Adherence to these rules is mandatory for all contributions.

## 1. Architectural Principles

### Clean Architecture & Layer Separation
The project follows a strict layered architecture. No layer may skip an intermediate layer:

1.  **UI Layer (Compose)**: Displays state and captures user input.
2.  **ViewModel**: Manages UI state and delegates to the Runtime.
3.  **Runtime (Facade)**: The unique public entry point for all infrastructure and platform services.
4.  **Managers**: Coordinate specific domain logic and services.
5.  **Repositories**: Abstraction for data access from multiple sources.
6.  **Datasources (Room / Firestore / SharedPreferences)**: Low-level data access implementations.
7.  **DAOs**: Room-specific data access objects.

### Offline First
- All user actions must be persisted locally in **Room** first.
- Data synchronization with the **Cloud (Firestore)** must be handled asynchronously via the **SyncQueue**.
- The application must be fully functional without an internet connection.

## 2. Dependency Management
- **Core Package**: Foundational code. Must NOT depend on feature packages (`asd`, `cc`, `fov`).
- **Feature Packages**: Depend on `core`. Must NOT depend on each other.
- **Dependency Injection**: Use constructor injection where possible. Avoid direct access to global objects like `AsdGraph` from the UI layer.

## 3. Model Separation
- **Domain Models**: Plain Kotlin classes used in UI, ViewModels, and Runtime.
- **Room Entities**: Annotated classes for local persistence. Must be mapped to Domain Models before reaching the UI.
- **Firestore DTOs**: Data Transfer Objects for cloud synchronization. Must be mapped to Domain Models.

## 4. Naming Conventions
- **Files**: Use descriptive names with domain prefixes (e.g., `AsdRepository.kt` instead of `repository.kt`).
- **Classes**: Avoid generic suffixes like `Helper`, `Utils`, `Common`, or `Misc`. Use specific names (e.g., `AsdCoordinateValidator.kt`).
- **Managers**: Use the `UrbanManager` suffix (e.g., `UrbanIdentityManager`).

## 5. Thread Safety & Coroutines
- Use `Dispatchers.IO` for all I/O, database, and network operations.
- Ensure that no blocking calls are made on the `Main` thread.
- Use `Flow` for reactive data streams from Repositories to UI.

## 6. Pull Request Checklist
- [ ] Code follows the defined layered architecture.
- [ ] No direct infrastructure calls (Room/Firestore/Prefs) from the UI layer.
- [ ] Domain models are separated from Entities and DTOs.
- [ ] All new files follow naming conventions.
- [ ] Dispatchers are correctly used for background tasks.
- [ ] Offline First principle is maintained.
- [ ] Unit tests are included for new business logic.
- [ ] `ARCHITECTURE.md` is updated if there are structural changes.

## 7. Guidelines for New Features
1.  Define the **Domain Models**.
2.  Create **Room Entities** and **DAOs** for local persistence.
3.  Implement the **Repository** to handle local/remote coordination.
4.  Expose the feature through a **Manager** and add it to **UrbanRuntime**.
5.  Implement the **ViewModel** to expose state to the UI.
6.  Create the **Compose UI**.
