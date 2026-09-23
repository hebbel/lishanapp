# Lishan

En flashcard-app til Android. Hovedskærmen er en liste over decks; et tryk på et deck viser dets kort ét ad gangen (tryk = næste side, swipe venstre = næste kort; begge starter forfra efter sidste). Data ligger i en Room-database, der ved første start får decket "Dyr" (hund/dog/perro, kat/cat). Et kort har 1–8 sider; kun sider med indhold vises.

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
- Git-repo på branch `main` med remote `origin` → https://github.com/hebbel/lishanapp (GitHub-konto `hebbel`). Commit-beskeder skrives på dansk.
- Room 2.8 (via KSP) til lagring. Ingen ViewModel eller Navigation Compose endnu — navigation er en simpel `selectedDeck`-variabel i `LishanApp`.
- Databaseændringer: tæl `version` op i `LishanDatabase`, skriv en `Migration` i `data/Migrations.kt`, tilføj den i `addMigrations(...)`, og skriv en test i `androidTest/.../MigrationTest.kt`. Room gemmer skemaet for hver version i `app/schemas/` (skal i git). Der er ingen destruktiv fallback: mangler en migration, går appen ned i stedet for at slette data. Startdata lægges kun ind ved en helt ny installation.
- Migrationstests kører på emulatoren: `./gradlew connectedDebugAndroidTest`. Unit tests: `./gradlew testDebugUnitTest`.
- Kode i `app/src/main/java/com/example/lishan/`:
  - `model/` — `Deck`, `Flashcard`, `CardSide` (Room-tabeller) og `FlashcardWithSides` (kort + sider)
  - `data/` — `DeckDao`, `LishanDatabase` (inkl. startdata), `Migrations.kt`
  - `ui/LishanApp.kt` — rod-UI, vælger skærm
  - `ui/decklist/`, `ui/deck/`, `ui/flashcard/` — skærme og komponenter; tema i `ui/theme/`

Avast's HTTPS-scanning skal være slået fra — ellers kan Gradle ikke hente nye afhængigheder (Java stoler ikke på Avasts certifikat).

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

Hvis buildet fejler med `Unable to delete directory ...`, skyldes det mapper med Windows-attributten ReadOnly (Gradle kan ikke slette dem, men `rm -rf` kan). Ret det med `attrib -R "C:\Users\sjheb\code\lishanapp\*" /S /D` i PowerShell og byg igen.

Emulator: Pixel 8 AVD (Android 17), `emulator-5554`.
