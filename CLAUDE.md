# Lishan

En flashcard-app til Android. Forsiden viser mapper (øverst) og de decks, der ikke ligger i en mappe; en mappe indeholder decks (ét niveau, ingen mapper i mapper). En mappe med `courseId` er et synkroniseret kursus, uden er den oprettet manuelt. Et tryk på et deck viser dets kort ét ad gangen (tryk = næste side, swipe venstre = næste kort; begge starter forfra efter sidste). Data ligger i en Room-database, der ved første start får decket "Dyr" (hund/dog/perro, kat/cat). Et kort har 1–8 sider; kun sider med indhold vises. Hvert kort har desuden `notes` (brugerens egne noter, vises under kortet, røres aldrig af synkronisering) og `comment` (fra serverens `flashcomment`; vises foreløbig kun i kortformularen). Hvert deck kan have labels på sidepositionerne (fx Dansk/Engelsk/Spansk); de vises på kortet og som feltnavne i kortformularen og redigeres sammen med deckets navn. "+" på forsiden giver valget mellem nyt deck, ny mappe og "Forbind kursus"; "+" i en mappe opretter et deck i den; "+" i et deck opretter et kort. Decks redigeres (navn + labels), flyttes mellem mapper/forsiden og slettes via ⋮-menuen i listen; mapper omdøbes/slettes (med deres decks) samme sted; inde i et deck kan det viste kort rettes/slettes, og ←-pilen ved deck-navnet går tilbage til listen. Sletning bekræftes i en dialog (`ui/ConfirmDeleteDialog.kt`).

## Arbejdsform

- Brugeren er ved at lære Android-udvikling og skriver på dansk. Svar på dansk.
- Byg appen **langsomt og i små trin**, så brugeren kan følge med i, hvordan strukturen vokser frem.
  - Ét koncept ad gangen (fx en datamodel, så en skærm, så navigation) — ikke hele features i ét hug.
  - Forklar kort *hvorfor* en fil/klasse/pakke tilføjes, og hvor den passer ind.
  - Tilføj ikke biblioteker eller arkitekturlag (Room, Hilt, ViewModel osv.), før der er et konkret behov, og nævn det, når det sker.

## Teknik

- Kotlin + Jetpack Compose (Material 3), Gradle med Kotlin DSL og version catalog (`gradle/libs.versions.toml`).
- Pakke / applicationId: `dk.lishan.app`
- minSdk 24, targetSdk/compileSdk 37
- Git-repo på branch `main` med remote `origin` → https://github.com/hebbel/lishanapp (GitHub-konto `hebbel`). Commit-beskeder skrives på dansk.
- Room 2.8 (via KSP) til lagring. Opbygning: skærm → `LishanViewModel` → `DeckRepository` → `DeckDao`. Ingen Navigation Compose endnu — navigation er `Screen` (sealed interface i `ui/Screen.kt`) + én state-variabel i `LishanViewModel`, som også står for tilbage-knappen (`back()`). Handlinger kører i `viewModelScope`, så de ikke afbrydes, når telefonen drejes. Al data går gennem `DeckRepository` (appens eneste indgang til data; i dag kun Room, senere også serveren). `LishanApp` viser bare det, ViewModel'en siger, og sender tryk videre; skærmene selv kender hverken ViewModel, repository eller database.
- UI-tilstand, der skal overleve at telefonen drejes (skærm, valgt kort/side, åbne dialoger, tekst i formularer), gemmes med `rememberSaveable` — ikke `remember`. Skærmen gemmes i ViewModel'ens `SavedStateHandle` via `ScreenSaver` i `ui/Screen.kt` (tal og tekst i en liste), så den også overlever, at Android lukker appen i baggrunden; en ny `Screen`-variant skal også tilføjes i `ScreenSaver` og `ScreenSaverTest`. Ren skærmtilstand (åbne dialoger, tekst i felter) bruger `rememberSaveable` i skærmene. Parcelize blev prøvet, men dets compiler-plugin kobler sig ikke på med AGP's indbyggede Kotlin i dette projekt.
- JSON: kotlinx.serialization 1.9.0 (plugin `org.jetbrains.kotlin.plugin.serialization`) virker med AGP's indbyggede Kotlin — afprøvet i `JsonTest`. Brug `Json { ignoreUnknownKeys = true }` til serverens svar. Serialization-versionen skal passe til Kotlin-versionen (1.9 ↔ Kotlin 2.2).
- Databaseændringer: tæl `version` op i `LishanDatabase`, skriv en `Migration` i `data/Migrations.kt`, tilføj den i `addMigrations(...)`, og skriv en test i `androidTest/.../MigrationTest.kt`. Room gemmer skemaet for hver version i `app/schemas/` (skal i git). Der er ingen destruktiv fallback: mangler en migration, går appen ned i stedet for at slette data. Startdata lægges kun ind ved en helt ny installation.
- Instrumenterede tests (DAO + migrations) kører på emulatoren: `./gradlew connectedDebugAndroidTest`. Unit tests: `./gradlew testDebugUnitTest`. `gradle.properties` har `leaveApksInstalledAfterRun=true`, så en testkørsel ikke afinstallerer appen og sletter dens data.
- Kode i `app/src/main/java/dk/lishan/app/`:
  - `model/` — `Folder`, `Deck`, `Flashcard`, `CardSide`, `DeckSideLabel` (Room-tabeller) og `FlashcardWithSides` (kort + sider)
  - Sider og labels har en fast position (1–8). En kortside beholder sin position, også når sider før den er tomme, så den passer til deckets label. I UI'et sendes de rundt som "positionelle" lister (plads 0 = side 1, tomme strenge for huller) — se `toPositional()` og `sidesByPosition()`.
  - `data/` — `AppServices`, `DeckRepository`, `DeckDao`, `LishanDatabase` (inkl. startdata), `Migrations.kt`; `data/remote/` (API), `data/auth/` (login og tokens), `data/sync/` (flettereglerne)
  - `ui/LishanViewModel.kt` — aktuel skærm, navigation og handlinger; `ui/Screen.kt` — skærmtyperne og `ScreenSaver`
  - `ui/LishanApp.kt` — rod-UI: viser den skærm, ViewModel'en siger, "+"-knappen og dialogen "Ny mappe"
  - `ui/decklist/`, `ui/deck/`, `ui/deckform/`, `ui/cardform/` (bruges både til at oprette og rette), `ui/courses/` ("Forbind kursus"), `ui/flashcard/` — skærme og komponenter; tema i `ui/theme/`
- Lishan-serverens API er beskrevet i `docs/app-api.md` (i produktion på `https://lishan.fak.dk`). `docs/server-api-prompt.md` er prompten, det blev bygget ud fra.
- Login og server: `data/AppServices.kt` opretter repository'et én gang. Er `lishan.clientId` sat i `gradle.properties`, bruges den rigtige server (`HttpLishanApi` med OkHttp, adresse og klient fra `BuildConfig`); er den tom, bruges `FakeLishanApi` uden login.
  - Login: OAuth 2 Authorization Code + PKCE i browseren via AppAuth (`data/auth/LoginService.kt`), redirect `dk.lishan.app:/oauth2redirect`. Skærmen "Forbind kursus" viser "Log ind"/"Log ud".
  - Tokens (access 1 time, refresh 90 dage og skiftes ved hver fornyelse) gemmes krypteret med en AES-nøgle i Android Keystore (`KeystoreTokenStore`, filen `lishan_auth.xml`, holdt ude af backup). `HttpLishanApi` fornyer selv tokens ved udløb eller `401` – kun én fornyelse ad gangen (Mutex) – og kaster `NotLoggedInException`, når serveren afviser fornyelsen; serverfejl bliver `ApiException`.
  - `HttpLishanApiTest` tester mod MockWebServer med svar fra `docs/app-api.md`.
- Kursussynkronisering:
  - "Forbind kursus" henter kun kursets lektionsliste; lektionerne bliver decks i kursets mappe, som ikke er hentet (gråt, ↓). Et tryk henter kortene (✓). Når kursusmappen åbnes, hentes listen igen: nye lektioner vises gråt, ændrede hentede lektioner får ↻ (hent igen manuelt), forsvundne lektioner markeres (hentede) eller fjernes (ikke hentede).
  - Et decks tilstand (`Deck.syncState`) beregnes ud fra `lessonId`, `serverHash`, `downloadedHash`, `removedOnServer` og `locallyModified`; "ude af sync" (↻) = serverens fingeraftryk (hash) ≠ det, lektionen blev hentet med; "ændret af dig" (✎) = brugeren har rettet sider/kommentar i eller slettet et kursuskort (noter og egne kort tæller ikke). ↻ vises før ✎. Hentning igen ("Gendan fra kurset" i ⋮, eller ↻) genopretter slettede kursuskort og overskriver rettelser – det bekræftes først, når decket er ændret af brugeren – og nulstiller ✎.
  - Markeringen sættes i `DeckRepository.updateCard`/`deleteCard` (sammenligner med `SyncRules.sameSides`) – derfor skal UI'et altid rette/slette kort via repository'et.
  - Flettereglerne ligger i `data/sync/SyncRules.kt` (rene funktioner): serverens tekst vinder, undtagen når den er tom; noter røres aldrig; egne kort (`wordId = null`) røres ikke. Kort genkendes på deck + `wordId`.
  - `data/remote/`: `LishanApi` (interface), `ApiModels.kt` (serverens JSON-format), `HttpLishanApi` (den rigtige server), `FakeLishanApi` (demokursus; bruges uden `lishan.clientId` og i `SyncTest`). Kursusmapper har en testknap "Test: simulér en ændring på serveren", når det falske API bruges.
  - Et redigeret decks navn gemmes med `renameDeck` (ikke `@Update` på hele decket), så synkroniseringsfelterne ikke nulstilles.
- Ikoner lægges ind som vektorfiler i `res/drawable/` (fx `ic_more_vert.xml`) i stedet for at bruge biblioteket material-icons.

Avast opsnapper HTTPS, og Java stoler ikke på Avasts certifikat, så Gradle kan ikke hente nye afhængigheder. Der er lagt undtagelser ind i Avast for `dl.google.com`, `repo.maven.apache.org`, `plugins.gradle.org` og `services.gradle.org`. Fejler en download med "could not resolve", så tjek certifikatudstederen med `openssl s_client -connect <host>:443` — står der "Avast", mangler der en undtagelse.

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
$ADB shell monkey -p dk.lishan.app -c android.intent.category.LAUNCHER 1
$ADB exec-out screencap -p > screen.png   # skærmbillede til at verificere UI
```

Hvis buildet fejler med `Unable to delete directory ...`, skyldes det mapper med Windows-attributten ReadOnly (Gradle kan ikke slette dem, men `rm -rf` kan). Ret det med `attrib -R "C:\Users\sjheb\code\lishanapp\*" /S /D` i PowerShell og byg igen.

Emulator: Pixel 8 AVD (Android 17), `emulator-5554`.

- Æ/Ø/Å fra PC-tastaturet når ikke frem, medmindre Androids fysiske tastaturlayout i emulatoren er sat til dansk; skærmtastaturet (Gboard) kræver dansk tilføjet som sprog. Appen selv filtrerer ikke tegn (se `UnicodeInputTest`). `adb shell input text` kan kun sende ASCII.
- I Git Bash: sæt `export MSYS_NO_PATHCONV=1` før adb-kommandoer med enhedsstier (fx `/sdcard/...`), ellers laves de om til Windows-stier.
- Hvis tryk ikke når frem til appen, og logcat viser `Not sending touch gesture ... NO_INPUT_CHANNEL` eller en ANR "Application does not have a focused window", er det emulatoren: `$ADB reboot`.
