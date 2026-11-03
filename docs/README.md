# Adhan Documentation Index

Welcome to the comprehensive documentation system for the **Adhan** native Android application.

This documentation is written to accompany the codebase step by step. Whether you are learning Android development, Kotlin, Jetpack Compose, or advanced background execution, each topic is broken down with real code references, architecture diagrams, step-by-step executions, and revision exercises.

---

## Recommended Reading Order

For someone learning native Android development or exploring this codebase for the first time, follow this reading path:

| Order | Document | Purpose & Summary |
| :---: | :--- | :--- |
| **1** | [Project Overview](PROJECT_OVERVIEW.md) | Understand the high-level mission, core requirements, constraints (no background location, no cloud, Tunisian calculation), and design goals. |
| **2** | [Tech Stack](TECH_STACK.md) | Explore every tool, plugin, library, and framework chosen for this project and the technical justification for each. |
| **3** | [Build and Gradle](BUILD_AND_GRADLE.md) | **Crucial foundational step**: Learn what Gradle is, how the Gradle Wrapper works, what `assembleDebug` executes, version catalogs, and how caching works. |
| **4** | [Project Structure](PROJECT_STRUCTURE.md) | A complete tour of the directory hierarchy, files, and where UI, domain, data, and configuration live. |
| **5** | [Android Fundamentals](ANDROID_FUNDAMENTALS.md) | Master Android fundamentals: Application class, Activity lifecycle, Context, Manifest, Intent system, and permission model. |
| **6** | [Kotlin Fundamentals](KOTLIN_FUNDAMENTALS.md) | Understand Kotlin language features used in our codebase: coroutines, Flow, sealed interfaces, data classes, and extension functions. |
| **7** | [Jetpack Compose & UI](JETPACK_COMPOSE.md) | Modern declarative UI: composables, state management, recomposition, Material 3, theming, and layout mechanics. |
| **8** | [Architecture](ARCHITECTURE.md) | MVVM architecture, unidirectional data flow (UDF), domain boundaries, and data layer separation. |
| **9** | [Data and Persistence](DATA_AND_PERSISTENCE.md) | Room database, DAOs, entities, migrations, DataStore, and persistent user preferences. |
| **10** | [Prayer Time Calculation](PRAYER_TIME_CALCULATION.md) | Solar astronomy, Tunisian official calculation conventions, equation of time, solar depression angles, and validation against official timetables. |
| **11** | [Alarms and Notifications](ALARMS_AND_NOTIFICATIONS.md) | The heart of Adhan: AlarmManager, exact alarm semantics, broadcast receivers, notification channels, battery optimizations, and boot recovery. |
| **12** | [Testing and Debugging](TESTING_AND_DEBUGGING.md) | Unit tests, Android test harness, Logcat debugging, and verifying schedules on real devices. |
| **13** | [Decisions and Tradeoffs](DECISIONS_AND_TRADEOFFS.md) | Engineering decision log explaining why specific paths were chosen and alternatives rejected. |
| **14** | [Learning Notes](LEARNING_NOTES.md) | Revision notebook: confusing concepts simplified, common pitfalls to avoid, and key takeaways. |
| **15** | [Changelog](CHANGELOG.md) | Step-by-step progress tracking each development stage. |

---

## Documentation Standards

Every document in this directory adheres to strict educational and technical standards:
1. **Accurate to Real Code**: All paths, function names, and Gradle versions correspond to the real files in the repository.
2. **Deep Concept Breakdowns**: Concepts are explained with **What**, **Why**, **How**, **Where it appears**, **Code examples**, **Common mistakes**, **Alternatives**, and **Revision questions**.
3. **No Phantom Claims**: Features and tests are marked according to their real status (`Implemented & Verified`, `In Progress`, or `Planned`).

