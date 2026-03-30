# FinKeeper24 KMP — Сборка и запуск

Kotlin Multiplatform проект с поддержкой Android, iOS, macOS, Windows и Linux.

## Требования

- JDK 11+
- Gradle Wrapper (`./gradlew`)
- Android SDK / Android Studio для Android-сборки
- Xcode 15+ для iOS
- на Windows для MSI может понадобиться WiX Toolset

## Android

### Debug APK

```bash
./gradlew :composeApp:assembleDebug

./gradlew :composeApp:installDebug

adb shell am start -n ru.homebudget.finkeeper/.MainActivity
```

### Release APK

```bash
./gradlew :composeApp:assembleRelease
```

Результат: `composeApp/build/outputs/apk/release/composeApp-release.apk`

Если в корне проекта присутствует `keystore.properties`, он используется для release-подписи.

## iOS

### Проверка компиляции

```bash
./gradlew composeApp:compileKotlinIosSimulatorArm64
```

### Запуск

Откройте `iosApp/iosApp.xcodeproj` в Xcode и соберите приложение на симуляторе или устройстве.

Для реального устройства нужно указывать реальный адрес сервера, а не `localhost`.

## Desktop

### Локальный запуск

```bash
./gradlew :composeApp:run
```

### Сборка desktop-дистрибутива для текущей ОС

```bash
./gradlew :composeApp:packageDistributionForCurrentOS
```

### Платформенные packaging-задачи

```bash
./gradlew :composeApp:packageDmg
./gradlew :composeApp:packagePkg
./gradlew :composeApp:packageExe
./gradlew :composeApp:packageMsi
./gradlew :composeApp:packageDeb
./gradlew :composeApp:packageRpm
```

Артефакты находятся в `composeApp/build/compose/binaries/main/`.

### Альтернативно для каждой платформы отдельно

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
Результат: `composeApp/build/compose/binaries/main/app/FinKeeper24.app`

### Сборка DMG-образа
```bash
./gradlew :composeApp:packageDmg
```
Результат: `composeApp/build/compose/binaries/main/dmg/FinKeeper24-1.1.0.dmg`

### Сборка PKG-установщика
```bash
./gradlew :composeApp:packagePkg
```
Результат: `composeApp/build/compose/binaries/main/pkg/FinKeeper24-1.1.0.pkg`

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
Результат: `composeApp/build/compose/binaries/main/app/FinKeeper24/`

### Сборка MSI-установщика
```bash
./gradlew :composeApp:packageMsi
```
Результат: `composeApp/build/compose/binaries/main/msi/FinKeeper24-1.1.0.msi`

> **Примечание:** Для сборки MSI необходим [WiX Toolset](https://wixtoolset.org/).

### Сборка EXE-установщика
```bash
./gradlew :composeApp:packageExe
```
Результат: `composeApp/build/compose/binaries/main/exe/FinKeeper24-1.1.0.exe`

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
Результат: `composeApp/build/compose/binaries/main/app/FinKeeper24/`

### Сборка DEB-пакета
```bash
./gradlew :composeApp:packageDeb
```
Результат: `composeApp/build/compose/binaries/main/deb/finkeeper24_1.1.0-1_amd64.deb`

### Сборка RPM-пакета
```bash
./gradlew :composeApp:packageRpm
```
Результат: `composeApp/build/compose/binaries/main/rpm/finkeeper-1.1.0-1.x86_64.rpm`

---

## Тесты

### Все common/unit tests

```bash
./gradlew composeApp:testDebugUnitTest
```

### Конкретный тест-класс

```bash
./gradlew composeApp:testDebugUnitTest --tests "ru.homebudget.finkeeper.util.FormattersTest"
```

## Очистка

```bash
./gradlew clean
```

## Связанные документы

- обзор KMP-клиента: `README.md`
- актуальная документация по sync: `../docs_and_instructions/current_sync_implementation.md`
