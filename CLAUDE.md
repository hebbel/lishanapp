# Lishan

En flashcard-app til Android. Projektet er i en tidlig fase: der er en `Flashcard`-datamodel og en `FlashcardView`, som `MainActivity` midlertidigt viser direkte.

## Arbejdsform

- Brugeren er ved at lære Android-udvikling og skriver på dansk. Svar på dansk.
- Byg appen **langsomt og i små trin**, så brugeren kan følge med i, hvordan strukturen vokser frem.
  - Ét koncept ad gangen (fx en datamodel, så en skærm, så navigation) — ikke hele features i ét hug.
  - Forklar kort *hvorfor* en fil/klasse/pakke tilføjes, og hvor den passer ind.
  - Tilføj ikke biblioteker eller arkitekturlag (Room, Hilt, ViewModel osv.), før der er et konkret behov, og nævn det, når det sker.

## Teknik

- Kotlin + Jetpack Compose (Material 3), Gradle med Kotlin DSL og version catalog (`gradle/libs.versions.toml`).
- Pakke / applicationId: `com.example.lishan`
- minSdk 24, targetSdk/compileSdk 37
- Git-repo på branch `main` (kun lokalt, ingen remote endnu). Commit-beskeder skrives på dansk.
- Kode: `app/src/main/java/com/example/lishan/` (`MainActivity.kt`, tema i `ui/theme/`)

## Byg og kør

`JAVA_HOME` er ikke sat på maskinen — brug Android Studios medfølgende JDK:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew installDebug        # bygger og installerer på kørende emulator
```

Start appen og tjek enheder (adb ligger ikke i PATH):

```bash
ADB=/c/Users/sjheb/AppData/Local/Android/Sdk/platform-tools/adb.exe
$ADB devices
$ADB shell monkey -p com.example.lishan -c android.intent.category.LAUNCHER 1
$ADB exec-out screencap -p > screen.png   # skærmbillede til at verificere UI
```

Hvis buildet fejler med `Unable to delete directory ...app\build\intermediates\...`, er det en kortvarig fillås. Projektet lå tidligere i `Documents` og blev synkroniseret af Google Drive; det er flyttet til `C:\Users\sjheb\code` for at undgå det. Avast kan også være årsagen. Slet de nævnte mapper med `rm -rf` og byg igen.

Emulator: Pixel 8 AVD (Android 17), `emulator-5554`.
