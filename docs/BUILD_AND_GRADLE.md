# Build System & Gradle Architecture

Understanding Gradle is essential to mastering Android engineering. Android applications are not built with simple single-command compilers; they undergo multi-stage transformations involving resource merging, symbol processing, Java/Kotlin compilation, bytecode dexing, optimization, and packaging.

---

## 1. What is Gradle?

**Gradle** is an advanced, highly extensible build automation tool designed to handle complex projects. In Android, Gradle:
1. Resolves all remote library dependencies from repositories like Google Maven and Maven Central.
2. Coordinates compilation of Kotlin and Java code.
3. Compiles XML layouts, drawables, and assets using the Android Asset Packaging Tool (`aapt2`).
4. Converts standard JVM bytecode (`.class`) into Dalvik Executable format (`.dex`) for the Android runtime (ART).
5. Packages everything into an `.apk` (Android Application Package) or `.aab` (Android App Bundle) and signs it.

---

## 2. What is the Gradle Wrapper (`gradlew`)?

You will notice two files in the project root: `gradlew` (for Linux/macOS shell) and `gradlew.bat` (for Windows Command Prompt/PowerShell), accompanied by `gradle/wrapper/gradle-wrapper.jar` and `gradle/wrapper/gradle-wrapper.properties`.

### Why we need it
Without the wrapper, every developer or build machine would have to manually install Gradle, ensure the exact matching version is installed, and configure system PATH variables. A version mismatch between developers causes mysterious build failures.

### How it works
When you execute:
```powershell
.\gradlew assembleDebug
```
1. `gradlew.bat` reads `gradle/wrapper/gradle-wrapper.properties`.
2. It inspects `distributionUrl` (e.g. `https://services.gradle.org/distributions/gradle-8.11.1-bin.zip`).
3. If Gradle 8.11.1 is not already cached locally on your machine (under `C:\Users\<user>\.gradle\wrapper\dists`), the script automatically downloads the binary, unpacks it, and caches it.
4. It hands off execution to that verified Gradle binary.

---

## 3. The Android Gradle Plugin (AGP)

Gradle by itself is a generic build tool (it knows how to compile standard Java, manage tasks, etc.). It does **not** natively know what an Android Manifest is, what a DEX file is, or how Android resource merging works.

**AGP (`com.android.application`)** is the official plugin maintained by Google that teaches Gradle how to build Android apps.

### Version Compatibility Matrix
AGP and Gradle must be strictly compatible. In this project:
- **Gradle Version**: `8.11.1`
- **Android Gradle Plugin (AGP)**: `8.7.3`
- **Kotlin Version**: `2.0.21`
- **KSP Version**: `2.0.21-1.0.28`

```
┌────────────────────────────────────────────────────────┐
│                      Gradle 8.11.1                     │  (Build Engine)
└──────────────────────────┬─────────────────────────────┘
                           │ loads
┌──────────────────────────▼─────────────────────────────┐
│          Android Gradle Plugin (AGP) 8.7.3             │  (Android-specific tasks)
└──────────────┬───────────────────────────┬─────────────┘
               │                           │
┌──────────────▼───────────┐ ┌─────────────▼─────────────┐
│ Kotlin Android & Compose │ │ KSP (Symbol Processing)   │
│         2.0.21           │ │      2.0.21-1.0.28        │
└──────────────────────────┘ └───────────────────────────┘
```

---

## 4. What Happens During `.\gradlew assembleDebug`?

When you run `assembleDebug`, Gradle executes a DAG (Directed Acyclic Graph) of tasks. Here is the step-by-step breakdown:

1. **`checkKotlinGradlePluginConfigurationErrors`**: Validates Kotlin compiler arguments and toolchain compatibility.
2. **`processDebugMainManifest` & `processDebugManifest`**: Merges `AndroidManifest.xml` from your app with manifests bundled in dependencies (e.g. Room, ActivityCompose).
3. **`generateDebugResources` & `mergeDebugResources`**: Compiles vector drawables, strings, colors, and layout resources with `aapt2`.
4. **`kspDebugKotlin`**: Kotlin Symbol Processing scans annotations like `@Entity`, `@Dao`, `@Database` (Room) and generates Kotlin source code before main compilation.
5. **`compileDebugKotlin`**: Compiles your Kotlin application code (`MainActivity.kt`, theme files) alongside KSP generated classes into JVM bytecode.
6. **`dexBuilderDebug` & `mergeExtDexDebug`**: Converts Java bytecode into `.dex` files runnable by Android ART.
7. **`packageDebug`**: Bundles the `.dex` files, compiled resources (`resources.arsc`), and assets into `Adhan-1.0-debug.apk` (named in `app/build.gradle.kts`).
8. **`assembleDebug`**: Anchors the debug build lifecycle, outputting the final APK to `app/build/outputs/apk/debug/app-debug.apk`.

---

## 5. Dependency Caching: Why the First Build Takes Time

When a new project is initialized:
- Gradle downloads the Gradle 8.11.1 runtime (~130 MB).
- AGP downloads required Android SDK components (e.g., `build-tools 34.0.0` or `35.0.0`).
- Remote dependencies (Jetpack Compose, Material 3, Room, Kotlin stdlib) are downloaded from `google()` and `mavenCentral()`.
- Everything is permanently cached in:
  - Wrapper: `C:\Users\<user>\.gradle\wrapper\dists`
  - Dependencies: `C:\Users\<user>\.gradle\caches\modules-2\files-2.1`

Subsequent builds reuse these cached artifacts, taking mere seconds thanks to Gradle's incremental compilation and build cache (`UP-TO-DATE` tasks).

---

## 6. Project Configuration Files Explained

### `settings.gradle.kts`
Defines the project name and where Gradle should search for plugins and dependencies:
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "PrayerAlarm"
include(":app")
```

### `gradle/libs.versions.toml` (Version Catalog)
The single source of truth for all dependency versions in modern Gradle:
```toml
[versions]
agp = "8.7.3"
kotlin = "2.0.21"
room = "2.6.1"

[libraries]
androidx-material3 = { group = "androidx.compose.material3", name = "material3" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
```
*Why this is superior*: It prevents version drift across multi-module projects and provides auto-completion in Android Studio.

---

## 7. Revision Questions

1. Why do we execute `.\gradlew` instead of a globally installed `gradle` command?
2. What is the role of `aapt2` during the build?
3. Where does Gradle cache downloaded JAR and AAR dependencies on Windows?
4. What is the difference between `minSdk`, `targetSdk`, and `compileSdk`?

