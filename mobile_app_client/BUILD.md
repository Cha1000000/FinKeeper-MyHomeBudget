# FinKeeper KMP — Сборка и запуск

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
