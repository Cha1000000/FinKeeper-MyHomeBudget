# FinKeeper — Сборка и запуск

Kotlin Multiplatform проект с поддержкой Android, iOS, macOS, Windows и Linux.

## Требования

- **JDK**: 11+
- **Gradle**: используется Gradle Wrapper (`./gradlew`)
- **Android**: Android SDK (API 35), Android Studio
- **iOS**: Xcode 15+, macOS
- **Desktop (macOS)**: macOS, JDK 11+
- **Desktop (Windows)**: Windows, JDK 11+, WiX Toolset (для MSI)
- **Desktop (Linux)**: Linux, JDK 11+

---

## Android

### Запуск (debug)
```bash
./gradlew :composeApp:installDebug
```

### Сборка Debug APK
```bash
./gradlew :composeApp:assembleDebug
```
Результат: `composeApp/build/outputs/apk/debug/composeApp-debug.apk`

### Сборка Release APK
```bash
./gradlew :composeApp:assembleRelease
```
Результат: `composeApp/build/outputs/apk/release/composeApp-release.apk`

> **Примечание:** Для Release-сборки необходим файл `keystore.properties` в корне проекта с параметрами подписи.

---

## iOS

### Проверка компиляции (Simulator ARM64)
```bash
./gradlew :composeApp:compileKotlinIosSimulatorArm64
```

### Запуск на симуляторе
Откройте `iosApp/iosApp.xcodeproj` в Xcode и запустите на выбранном симуляторе.

### Сборка для устройства
Откройте проект в Xcode, выберите целевое устройство и выполните `Product → Build`.

> **Примечание:** Для запуска на реальном устройстве необходимо указать IP-адрес сервера (не `localhost`).

---

## macOS

### Запуск (без сборки дистрибутива)
```bash
./gradlew :composeApp:run
```

### Сборка приложения (.app)
```bash
./gradlew :composeApp:createDistributable
```
Результат: `composeApp/build/compose/binaries/main/app/FinKeeper.app`

### Сборка DMG-образа
```bash
./gradlew :composeApp:packageDmg
```
Результат: `composeApp/build/compose/binaries/main/dmg/FinKeeper-1.1.0.dmg`

### Сборка PKG-установщика
```bash
./gradlew :composeApp:packagePkg
```
Результат: `composeApp/build/compose/binaries/main/pkg/FinKeeper-1.1.0.pkg`

---

## Windows

### Запуск (без сборки дистрибутива)
```bash
./gradlew :composeApp:run
```

### Сборка приложения (.exe)
```bash
./gradlew :composeApp:createDistributable
```
Результат: `composeApp/build/compose/binaries/main/app/FinKeeper/`

### Сборка MSI-установщика
```bash
./gradlew :composeApp:packageMsi
```
Результат: `composeApp/build/compose/binaries/main/msi/FinKeeper-1.1.0.msi`

> **Примечание:** Для сборки MSI необходим [WiX Toolset](https://wixtoolset.org/).

### Сборка EXE-установщика
```bash
./gradlew :composeApp:packageExe
```
Результат: `composeApp/build/compose/binaries/main/exe/FinKeeper-1.1.0.exe`

---

## Linux

### Запуск (без сборки дистрибутива)
```bash
./gradlew :composeApp:run
```

### Сборка приложения
```bash
./gradlew :composeApp:createDistributable
```
Результат: `composeApp/build/compose/binaries/main/app/FinKeeper/`

### Сборка DEB-пакета
```bash
./gradlew :composeApp:packageDeb
```
Результат: `composeApp/build/compose/binaries/main/deb/finkeeper_1.1.0-1_amd64.deb`

### Сборка RPM-пакета
```bash
./gradlew :composeApp:packageRpm
```
Результат: `composeApp/build/compose/binaries/main/rpm/finkeeper-1.1.0-1.x86_64.rpm`

---

## Тесты

### Запуск всех unit-тестов
```bash
./gradlew :composeApp:testDebugUnitTest
```

### Запуск конкретного тест-класса
```bash
./gradlew :composeApp:testDebugUnitTest --tests "ru.homebudget.finkeeper.util.FormattersTest"
```

---

## Очистка

```bash
./gradlew clean
```
