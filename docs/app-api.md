# Lishan app-API (v1)

API til Lishans flashcard-app. Appen **henter** kurser, lektioner og kort. Den sender ikke noget tilbage.

- Produktion: `https://lishan.fak.dk`
- Udvikling (Android-emulator): `http://10.0.2.2:8000` (PC'ens `php artisan serve`)

Alle svar er JSON i UTF-8. Unicode sendes uescaped (arabisk, æøå osv. står som tegn, ikke `\uXXXX`).

---

## 1. Login (OAuth 2 Authorization Code + PKCE)

Serveren er en OAuth 2-server (Laravel Passport). Login sker i telefonens browser på Lishans normale login-side (FAK-SSO i produktion). Appen ser aldrig brugerens adgangskode og har ingen hemmelig nøgle.

| | |
|---|---|
| Authorization endpoint | `GET {base}/oauth/authorize` |
| Token endpoint | `POST {base}/oauth/token` |
| `client_id` | tildeles, når klienten oprettes på serveren (dev: `7`, prod: se opsætning nedenfor) |
| `redirect_uri` | `dk.lishan.app:/oauth2redirect` |
| PKCE | påkrævet, `code_challenge_method=S256` |
| `scope` | tom (`""`) |
| Access token | gyldigt i **1 time** |
| Refresh token | gyldigt i **90 dage**. Hver fornyelse giver et nyt refresh token (det gamle bliver ugyldigt). |

På Android anbefales biblioteket **AppAuth** (`net.openid:appauth`). Det håndterer PKCE, Custom Tabs og redirect. I `build.gradle`: `manifestPlaceholders = [appAuthRedirectScheme: "dk.lishan.app"]`.

### Flow

1. Appen åbner browseren på:
   ```
   {base}/oauth/authorize?client_id=7&redirect_uri=dk.lishan.app:/oauth2redirect
     &response_type=code&scope=&state=<tilfældig>
     &code_challenge=<BASE64URL(SHA256(verifier))>&code_challenge_method=S256
   ```
2. Er brugeren ikke logget ind, sendes de til login (FAK-SSO i prod, `/offline/login` i udvikling). Er de allerede logget ind i browseren, springes login over.
3. Der er ingen "Tillad adgang?"-side for Lishans egen app. Browseren sendes direkte til
   `dk.lishan.app:/oauth2redirect?code=<kode>&state=<state>`.
4. Appen bytter koden til tokens (form-encoded eller JSON):
   ```
   POST {base}/oauth/token
   grant_type=authorization_code
   client_id=7
   redirect_uri=dk.lishan.app:/oauth2redirect
   code_verifier=<verifier>
   code=<kode>
   ```
   Svar:
   ```json
   {
     "token_type": "Bearer",
     "expires_in": 3600,
     "access_token": "eyJ0eXAiOiJKV1QiLCJh…",
     "refresh_token": "def50200…"
   }
   ```
5. API-kald sendes med headeren `Authorization: Bearer <access_token>`.
6. Når access token udløber (eller et kald giver `401`), fornyes det:
   ```
   POST {base}/oauth/token
   grant_type=refresh_token
   client_id=7
   refresh_token=<refresh_token>
   ```
   Svaret har samme form som i trin 4. **Gem det nye refresh token.**
   Giver fornyelsen en fejl (fx `401` `{"error":"invalid_request", …}`, hvis tokenet er udløbet eller tilbagekaldt), skal brugeren logge ind igen (trin 1).

Fejl fra `/oauth/*` følger OAuth-standarden: `{ "error": "…", "error_description": "…", "message": "…" }`.

Bemærk: Hvis brugeren logger ud af SSO i browseren, påvirker det ikke appens tokens. Brug `POST /api/v1/logout`.

---

## 2. Endpoints

Alle endpoints kræver `Authorization: Bearer <access_token>`.

### Fejl

Fejl har altid formen `{ "error": "<kode>" }`. Der sendes aldrig databasefejl eller SQL.

| Status | `error` | Betydning |
|---|---|---|
| 401 | `unauthenticated` | Intet, ugyldigt, udløbet eller tilbagekaldt token. Forny eller log ind igen. |
| 403 | `forbidden` | Kurset findes, men er skjult. |
| 404 | `not_found` | Ukendt kursus eller lektion, eller ugyldigt id-format. |
| 429 | `too_many_requests` | Mere end 60 kald i minuttet. Headeren `Retry-After` siger, hvor mange sekunder der skal ventes. |
| 500 | `server_error` | Fejl på serveren (bliver logget). |

### `GET /api/v1/me`

Den indloggede bruger.

```json
{ "id": 97, "username": "00374769", "name": "00374769" }
```

`name` er brugerens navn, hvis det findes, ellers brugernavnet.

### `POST /api/v1/logout`

Tilbagekalder det access token, der sendes med, og dets refresh token. Appen bør derefter slette sine tokens.

```json
{ "ok": true }
```

### `GET /api/v1/courses`

Kurser, brugeren har adgang til: alle kurser, der ikke er skjulte og har et sprog. Sorteret efter id.

```json
[
  {
    "id": 71,
    "name": "ARA24-26",
    "language": { "id": 1, "code": "ARA" },
    "sides": [
      { "position": 1, "label": "Dansk",   "rtl": false },
      { "position": 2, "label": "Msasp",   "rtl": true },
      { "position": 3, "label": "Msapi",   "rtl": true },
      { "position": 4, "label": "Engelsk", "rtl": false },
      { "position": 5, "label": "Msamas",  "rtl": true }
    ]
  },
  {
    "id": 60,
    "name": "arab17-19EA",
    "language": { "id": 3, "code": "EA" },
    "sides": [
      { "position": 1, "label": "dk",            "rtl": false },
      { "position": 2, "label": "eaar / eatr",   "rtl": true },
      { "position": 3, "label": "ea2ar / ea2tr", "rtl": true },
      { "position": 4, "label": "ea3ar / ea3tr", "rtl": true }
    ]
  }
]
```

Regler for `sides`:
- **Position** 1–5 svarer til kursets `flashsidea` … `flashsidee`. En tom side udelades, men **positionerne bevares**: er `flashsideb` tom, findes position 2 ikke, og `flashsidec` er stadig position 3.
- En side kan bestå af flere kolonner (fx `eaar,eatr`). Kolonner, der ikke findes i glosetabellen, og billedkolonnen `picurl` ignoreres. Er der ingen kolonner tilbage, udelades siden.
- **`label`**: kolonnernes `nicename` fra `tbl<sprog>_cols`, adskilt af `" / "`. Er `nicename` tom, bruges kolonnenavnet. Labels kan rettes af lærerne i Lishan.
- **`rtl`**: `true`, hvis sidens **første** kolonne har `style = rtl`.

### `GET /api/v1/courses/{courseId}/lessons`

Kursets lektioner uden kort, sorteret efter lektions-id som i databasen.

```json
[
  {
    "id": "1.01",
    "title": "Introduktion til talesprog - Arabisk Lærebog - kontekstforståelse",
    "part": "EA",
    "card_count": 28,
    "hash": "bc711749860f5f454f6d4bd3803ef5a52e1cae6b902dd855d843112452774b18"
  },
  {
    "id": "1.10",
    "title": "(ya ’ustāza maryam)(giddi wi sitti)",
    "part": "EA",
    "card_count": 30,
    "hash": "5d336d78b7b6e926765eb48119c89718dd5cfe7983c376dba5f75b12227e55aa"
  }
]
```

- `id` er en **tekst** i præcis databasens format: 1–3 cifre, punktum, 2 cifre (`"1.10"`, `"12.05"`). Brug den som nøgle.
- Deckets titel i appen: `"<id> <title>"`, fx `"1.10 (ya ’ustāza maryam)(giddi wi sitti)"`.
- `part` er kursusdelen (kan være `""`).
- `card_count` kan være `0`.
- Om `hash`: se afsnit 3.

### `GET /api/v1/courses/{courseId}/lessons/{lessonId}/cards`

Én lektions kort. `lessonId` skal have formatet ovenfor (fx `1.10`). Andre formater giver `404`.

```json
{
  "lesson": {
    "id": "1.50",
    "title": "(biṭā’a) (ṣūra min Iskandariyya)",
    "part": "EA",
    "hash": "13d8e8d44383aa8583e34e49269e267256864d0b62b3d1ed68a4439a76285395"
  },
  "cards": [
    { "word_id": 473, "order": 1, "sides": ["farbror", "عَمٌّ", "عُمُومٌ", "", ""], "category": "nomen", "comment": "770" },
    { "word_id": 517, "order": 2, "sides": ["morbror", "خَالٌ", "أَخْوَالٌ", "", ""], "category": "nomen", "comment": "3188" }
  ]
}
```

Regler for kort:
- **`word_id`** er glosens id i Lishan og ændrer sig aldrig. Et kort genkendes på (kursus-id, `word_id`).
- **`order`** er kortets plads i lektionen (1, 2, 3 …). Rækkefølgen kommer fra lærernes sortering i Lishan, og gloser uden sortering kommer sidst i den rækkefølge, de blev tilføjet. Samme glose kommer kun én gang pr. lektion.
- **`sides[i]`** er side `i + 1`. Arrayet er lige så langt som kursets højeste position, og **tomme eller manglende sider sendes som `""`**. Positionerne passer altså altid til kursets `sides`.
- **Flere kolonner på én side** (fx `eaar,eatr`): de ikke-tomme værdier sættes sammen med linjeskift `\n` i kolonnernes rækkefølge, fx `"كويس\nkuwayyis"`.
- **`category`**: værdien af kursets `flashcat`-kolonne, fx ordklasse. Kan være `""`.
- **`comment`**: værdien af kursets `flashcomment`-kolonne(r), fx frekvens. Flere kolonner sættes sammen med `\n`. Kan være `""`.
- **Tekst:** HTML fjernes, HTML-entities afkodes (`&nbsp;` → mellemrum), og mellemrum i start og slut trimmes. Linjeskift er altid `\n`. For egyptisk arabisk (`EA`) oversættes translitterationskolonnernes ASCII-pladsholdere til de rigtige tegn (`Z` → `ẓ`, `§` → `ع`, `ä` → `ā` …), og for latin (`LAT`) bliver `ä ë ï ö ü` til makroner (`ā ē ī ō ū`). Det er det samme, som hjemmesiden viser.
- Billeder og lyd kommer ikke med i v1.

---

## 3. Fingeraftryk (hash)

Hver lektion har en `hash` (64 hex-tegn), som er **den samme** i lektionslisten og i kort-svaret.

- Den er SHA-256 af et versionsmærke plus JSON af lektionens `cards`-array, præcis som det sendes: sorteret efter `order`, med felterne `word_id`, `order`, `sides`, `category` og `comment` i fast rækkefølge.
- Den **ændrer sig**, når en glose på lektionen ændres i en kolonne, der er på kortet (side, kategori eller kommentar), når gloser tilføjes, fjernes eller flyttes, og når kursets kortopsætning (flashsider) ændres.
- Den **ændrer sig ikke** ved ændringer, der ikke påvirker kortene, fx lektionstitlen eller en kolonne, der ikke vises på kortene. Titlen står i lektionslisten, så appen kan opdatere decktitlen hver gang.
- Den beregnes **live** ved hvert kald og caches ikke. Der er derfor ingen cache, der kan blive forældet, uanset hvor i Lishan gloserne ændres. Hele kursus 71 (352 lektioner, 9.478 kort) tager ca. 0,07 s.

Appen gemmer hashen for hver hentet lektion. Er den anderledes næste gang, listen hentes, er lektionen "ude af sync".

---

## 4. Udvikling

- I `.env`: `OFFLINE_AUTH=true`. Login-siden i browseren er da `/offline/login` (testbruger fra `config/offline_auth.php`).
- Start serveren, så emulatoren kan nå den: `php artisan serve --host=0.0.0.0 --port=8000`. Brug `http://10.0.2.2:8000` i appen.
- Android blokerer HTTP som standard. Tillad cleartext **kun** for `10.0.2.2` i en debug-`network_security_config.xml`. Produktion bruger HTTPS (`APP_ENV=production` tvinger HTTPS-links).
- Opret klienten lokalt én gang: `php artisan passport:keys` og `php artisan passport:client --public --name=lishan-app --redirect_uri=dk.lishan.app:/oauth2redirect`. Den udskrevne `Client ID` er appens `client_id`.
- Tests: `php vendor/bin/phpunit tests/Feature/AppApi`. De kører mod databasen `lishan_dk_test` (aldrig `lishan_dk`).
- Avast HTTPS-scanning: hvis emulatoren får certifikatfejl ved login på `id.fak.dk`, er det første mistænkte.

---

## 5. Opsætning på produktionsserveren (engang)

1. Kopiér filerne (deploy-bundle). `composer.lock` er ændret, så kør `composer install --no-dev -o`.
   `config/auth.php` er ændret (nye guards `api`/`passport_web`, provider `app_users`, SSO-provider `lishan-oidc-session`), og `config/passport.php` er ny. Prod's `config/auth.php` har tidligere afveget fra repoet, så sammenlign dem før kopiering.
   **OBS:** Det overskriver `vendor/`, inkl. den håndpatchede `vendor/xdavidwu/laravel-oidc-auth`. Patchen er nu flyttet ind i appen (`app/Auth/LishanOidcUser.php` og `LishanOidcSessionUserProvider.php`, registreret som auth-provider `lishan-oidc-session` i `config/auth.php`), så det er meningen. Kontrollér bagefter, at SSO-login på hjemmesiden virker.
2. `php artisan passport:keys`. Nøglerne lægges i `storage/`, som ikke er med i deploy-bundlen. De skal ligge på serveren og må ikke deles.
3. `php artisan passport:client --public --name=lishan-app --redirect_uri=dk.lishan.app:/oauth2redirect`, og notér `Client ID` til appen.
4. `php artisan config:cache && php artisan route:cache`, og genstart php-fpm (OPcache).
5. Kontrol: `curl https://lishan.fak.dk/api/v1/me` skal give `401 {"error":"unauthenticated"}`.
