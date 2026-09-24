# Prompt til Claude på serveren: API til Lishan-appen

> Kopiér alt under stregen ind i Claude Code i Lishan-projektets mappe (Laravel) på den computer,
> hvor den nyeste udgave af serveren ligger.

---

Jeg er ved at bygge en Android-app til Lishan (flashcards). Appen skal kunne logge ind med Lishans
eksisterende SSO-login og hente kurser og gloser herfra. Jeg har brug for, at du bygger et API til den
i dette Laravel-projekt. Jeg skriver dansk og er ved at lære, så forklar gerne kort, hvad du gør og hvorfor.

**Arbejd i denne rækkefølge, og stop efter trin 1 og 2, så jeg kan svare:**

1. **Undersøg først** – byg ingenting endnu. Beskrivelsen nedenfor er lavet ud fra en *ældre* kopi af
   projektet og databasen, så den kan være forkert. Find ud af og rapportér:
   - Hvordan SSO-login'et virker i dag (pakker, routes, config, hvilken brugertabel: `users` eller `bitauth_users`), og om Laravel Passport og/eller en OpenID Connect-pakke er i brug.
   - Hvordan man ser, hvilke kurser en bruger har adgang til (fx via `groups` / `groupcourse` / `bitauth_assoc`).
   - Om tabellerne og kolonnerne nedenfor findes som beskrevet.
2. **Foreslå en plan** med de konkrete ændringer, og spørg mig om det, der er uklart.
3. **Byg** API'et med tests.
4. **Skriv `docs/app-api.md`** med den færdige beskrivelse af endpoints og eksempler på JSON-svar.
   Den fil tager jeg med tilbage til app-projektet.

## Sådan ser databasen ud (fra den ældre kopi – tjek det)

- `languages`: ét sprog pr. række (fx 1 = ARA, 3 = EA, 5 = LAT). `hovedtabel` er sprogets glosetabel (fx `tblmsa`, `tblea`).
- `tbl<sprog>`: glosetabellen. Hvert sprog har **sine egne kolonnenavne**. `id` er glosens id.
- `tbl<sprog>_cols`: beskriver kolonnerne: `colname`, `nicename` (læsbart navn) og `style` (`rtl` = skrives fra højre mod venstre, fx arabisk).
- `courses`: `id`, `kursusnavn`, `languageid`, `hidden`, `deckname` og flashcard-opsætningen:
  - `flashsidea` … `flashsidee`: op til 5 kortsider. **Hver side er en kommasepareret liste af kolonner** i sprogets glosetabel, fx `eaar,eatr` (arabisk skrift + translitteration).
  - `flashcat`: en kolonne med en kategori, fx ordklasse.
  - `flashcomment`: en kolonne med en kommentar, fx frekvens.
- `lessons<kursus-id>` (én tabel pr. kursus): `lesid` (decimal i formatet XXX.XX), `olbetegnelse` (navn), `kursusdel`.
- `leswords<kursus-id>` (én tabel pr. kursus): `id` (= glosens id), `clesid` (= lektionens `lesid`), `ordern` (rækkefølge).
- `groups`, `groupcourse` (`groupid`, `courseid`), `group_institution_assoc`; brugere i `users` og/eller `bitauth_users`.
- Tabellerne `oauth_clients`, `oauth_access_tokens` osv. (Laravel Passport) samt `oidc_clients` og `open_i_d_providers` findes.

## Hvordan appen virker (det API'et skal understøtte)

- **Offline først.** Appen gemmer alt i sin egen database og virker helt uden net, når en lektion er hentet. Serveren bruges kun til at *hente* data. Appen sender ikke noget tilbage (endnu).
- **Et kursus bliver til en mappe i appen, og hver lektion til et deck.** Deckets titel bliver `"<lesid> <olbetegnelse>"`, fx `"12.05 Familie"`.
- **Når brugeren forbinder til et kursus, henter appen kun listen over lektioner**, ikke kortene. Lektionerne vises gråt. Brugeren vælger selv, hvilke lektioner der skal hentes.
- **Når kursusmappen åbnes, henter appen listen igen.** Nye lektioner vises gråt. Hentede lektioner, der er **ændret** på serveren, markeres "ude af sync", og brugeren kan hente dem igen.
  → Derfor skal hver lektion have et **fingeraftryk (hash)**, som ændrer sig, når og kun når lektionens kort ændrer sig.
- **Et kort i appen** har op til 8 sider på faste positioner (side 1, 2, 3 …), og tomme sider vises ikke. Hver position har et label pr. deck (fx "Dansk", "Arabisk"). Kortet har desuden en `comment` (fra `flashcomment`) og en `category` (fra `flashcat`). Brugerens egne noter ligger kun i appen.
- **Kort genkendes på (kursus-id, glose-id).** Ved gen-hentning overskriver appen sine felter med serverens, **undtagen når serverens felt er tomt**. Brugerens noter røres aldrig. Serveren skal derfor altid sende de samme glose-id'er for de samme gloser.

## Login

- Appen logger ind med **OAuth 2 Authorization Code + PKCE** via telefonens browser, dvs. det er Lishans normale (SSO-)login-side, brugeren ser. Appen må ikke se brugerens adgangskode, og der må ikke ligge en hemmelig nøgle i appen.
- Opret derfor en **offentlig klient uden secret** (i Passport: `php artisan passport:client --public`) med redirect-URI'en **`dk.lishan.app:/oauth2redirect`**.
- Appen skal have et **access token og et refresh token**, så brugeren ikke skal logge ind, hver gang der synkroniseres.
- API'et beskyttes med tokens (fx `auth:api` med Passport). Hvis SSO'en i dag er lavet på en måde, hvor det ikke passer, så sig det, og foreslå den bedste løsning.

## Endpoints (forslag – ret gerne navne og detaljer)

Alle svar er JSON i UTF-8. Versionér adressen: `/api/v1/...`.

1. `GET /api/v1/me` → den indloggede bruger (id, navn), så appen kan vise, hvem der er logget ind.
2. `GET /api/v1/courses` → de kurser, brugeren har adgang til (ikke `hidden`), fx:
   ```json
   [{
     "id": 71,
     "name": "ARA24-26",
     "language": { "id": 1, "code": "ARA" },
     "sides": [
       { "position": 1, "label": "Dansk",  "rtl": false },
       { "position": 2, "label": "Msasp",  "rtl": true }
     ]
   }]
   ```
   `sides` kommer fra `flashsidea`…`flashsidee`. Tomme flashsider udelades, men **positionerne bevares**: er `flashsideb` tom, findes position 2 ikke, og `flashsidec` er stadig position 3. `label` tages fra `nicename` i `tbl<sprog>_cols` (for en side med flere kolonner: foreslå en fornuftig regel). `rtl` er `true`, hvis sidens kolonner har `style = rtl`.
3. `GET /api/v1/courses/{courseId}/lessons` → kursets lektioner, uden kort:
   ```json
   [{ "id": "12.05", "title": "Familie", "part": "…", "card_count": 34, "hash": "…" }]
   ```
   Send `lesid` som en **tekst med præcis det format, der står i databasen** (fx `"12.05"`), så appen kan bruge det som nøgle. Sortér som i databasen.
4. `GET /api/v1/courses/{courseId}/lessons/{lessonId}/cards` → én lektions kort:
   ```json
   {
     "lesson": { "id": "12.05", "title": "Familie", "hash": "…" },
     "cards": [{
       "word_id": 1234,
       "order": 3,
       "sides": ["far", "أب\nab", ""],
       "category": "substantiv",
       "comment": "120"
     }]
   }
   ```
   - `sides[i]` er side `i + 1`. **Tomme sider sendes som `""`**, så positionerne passer til kursets `sides`.
   - En side med flere kolonner (fx `eaar,eatr`): **sæt de ikke-tomme værdier sammen med linjeskift** (`\n`), medmindre du finder en bedre løsning. Beskriv reglen i `docs/app-api.md`.
   - `hash` skal være **den samme** her og i lektionslisten.

## Fingeraftryk (hash)

- Beregn fx SHA-256 af en fast (kanonisk) udgave af lektionens kort: sorteret efter `order` og `word_id`, med faste felter og fast rækkefølge. Samme data skal altid give samme hash.
- Det skal være hurtigt nok til at beregne for alle lektioner i et kursus i ét kald. Cache gerne, men så skal cachen ryddes, når gloser eller lektioner ændres. Beskriv, hvordan du sikrer det.

## Sikkerhed og kvalitet

- Kun kurser, brugeren har adgang til. Andet giver `403`, og ukendte kurser og lektioner giver `404`. Ikke logget ind giver `401`. Fejl har en fast JSON-form, fx `{ "error": "…" }`.
- **Send aldrig databasefejl eller SQL til klienten.** Den eksisterende kode returnerer `$e->getMessage()`, og det skal det nye API ikke gøre.
- Tabelnavne som `lessons<id>` og `leswords<id>` bygges ud fra kursets id. **Id'et skal være et heltal, og tabellen skal findes**, før den bruges i SQL.
- Rate limiting på API'et.
- **Tests** (PHPUnit/feature tests): ingen adgang uden token, adgangskontrol pr. kursus, svarenes form, bevarede positioner ved tomme sider, at hashen er stabil og ændrer sig, når en glose ændres, og at unicode (arabisk, æøå) kommer uændret igennem.

## Udvikling og test

- Under udvikling kører appen i Android-emulatoren og når serveren på `http://10.0.2.2:8000` (PC'ens localhost). Sørg for, at login-flowet og redirect'en til `dk.lishan.app:/oauth2redirect` virker den vej. HTTP (uden S) må kun være tilladt i udvikling.
- Min PC har Avast med HTTPS-scanning. Nævn det, hvis noget peger på, at det giver problemer.
