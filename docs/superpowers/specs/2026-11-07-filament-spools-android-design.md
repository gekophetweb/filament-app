# Filament Spools — Android companion app (design)

Date: 2026-11-07
Status: Approved in conversation, pending spec review

## 1. Purpose

A personal Android companion for the **Filament Spools** tracker (Laravel app, API at
`<server>/api/v1`, docs at `/docs/api`, OpenAPI 3.1 at `/docs/api/openapi.json`).

Used **at the printer**: in under 10 seconds find the spool being loaded and mark it
**opened** (boxed → on holder) or **used up**, and see at a glance what is on the holder
and what is running out. Secondary: browse the catalog and add spools.

Success criteria:

- Pair a phone by scanning the Devices-page QR code.
- Home screen shows spools on the holder, products running out, and boxed spools, searchable.
- Open / Used up / Add / Edit / Remove work against the API and update the screen immediately.
- Lists still show (read-only) when the server is unreachable.

Out of scope: barcode or NFC spool scanning, offline write queue, background sync, widgets,
iOS, Play Store release / release signing.

## 2. Assumptions

- Personal use, side-loaded APK or run from Android Studio.
- Server is reached over the LAN over plain HTTP. The QR's `api` field may say `localhost`,
  so the server URL is always editable (e.g. `http://192.168.x.x:8000/api/v1`;
  emulator: `http://10.0.2.2:8000/api/v1`).
- Tokens may be read-only (`spools:read`) or read-write (`spools:write`). Write actions are
  hidden for read-only tokens.

## 3. API surface used

| Call | Use |
|---|---|
| `POST /pair` `{code, device_name}` → 201 token | Pairing (no auth) |
| `GET /me` | Validate token, user name, abilities |
| `GET /catalog?q,material,brand,line,color,owned,updated_since,per_page,page` | Catalog sync and filters |
| `GET /catalog/{id}` | Product refresh |
| `GET /spools?status,updated_since,per_page,page` | Spool sync (sync mode includes `deleted_at`) |
| `POST /spools` | Add spool |
| `GET/PATCH/DELETE /spools/{uuid}` | Detail, edit, remove (DELETE body `{note}`) |
| `POST /spools/{uuid}/open` | boxed → on_spool |
| `POST /spools/{uuid}/used-up` | boxed/on_spool → used_up |
| `GET /spools/{uuid}/events?page` | History |

All requests send `Accept: application/json` and `Authorization: Bearer <token>`.
Lists are paginated: follow `links.next` until null. Exact DTO fields follow the
`ProductData`, `SpoolData`, `SpoolEventData`, `Me`, `PairedDevice` schemas in `openapi.json`.
Note: `price` comes back as a string or null.

Rate limits: 120 reads/min, 30 writes/min and 1000/day, 300 calls/min/IP; 20 failed auths
block the IP for 10 minutes. The app never retries automatically.

## 4. Screens

Bottom navigation: **Printer · Catalog · Settings**. Pairing is shown instead while no token
is stored.

1. **Pairing**
   - Scan the QR (CameraX + ML Kit barcode). Expected JSON:
     `{"type":"filament-spools-pairing","api":"…","code":"…","expires_at":"…"}`.
     Other QR content is rejected with a message.
   - Manual alternative: paste the JSON, or type the server URL and code.
   - The server URL field is filled from the QR and stays editable. The device name defaults to the phone model.
   - On submit: `POST /pair` → store token → `GET /me` → first sync → Home.
   - Shows `expires_at` and warns when the code has expired.
2. **Printer (home)**
   - Search field that filters all sections locally (name, article number, color family).
   - *On the holder*: `on_spool` spools as cards (color swatch from `color_hex`/`color_hex_2`,
     name, line, location). Actions: **Used up** (confirm dialog), tap → detail.
   - *Running out*: products with on_spool_count > 0 and boxed_count = 0 (same definition
     as the API's `owned=running_out`), with a buy link when `buy_url` is set.
   - *Boxed*: `boxed` spools. Actions: **Open** (one tap + snackbar), **Used up** (confirm).
   - Pull to refresh = sync. Offline banner "Offline — last synced HH:MM" when the last
     sync failed on a network error.
3. **Catalog**
   - Search plus filters: material, brand, line, color family, owned (all / owned / not owned / boxed /
     on holder / running out). Filtering runs on the local cache.
   - Product detail: swatch, details, your counts, product/buy links, list of your spools
     of this product, **Add spool** form (status boxed/on holder, refill when `has_refill`,
     purchase date, price, location, notes).
4. **Spool detail**
   - All fields. **Edit** (PATCH, only changed fields), **Open** / **Used up** where the
     status allows, **Remove** (DELETE with optional note, confirm dialog: "for mistakes or
     spools sold/given away — not counted as used").
   - History list from `/events` (paged, loaded live; shows an offline message when unavailable).
5. **Settings**
   - Server URL (editing it signs out after confirmation), user name, token type
     (read-only / read-write), last sync time, **Sync now**, **Sign out** (deletes the
     local token and cache; the token stays valid on the server until revoked on the
     Devices page — the screen says so).

## 5. Architecture

Kotlin, Jetpack Compose (Material 3), single `:app` module, packages:

- `api` — Retrofit service, kotlinx.serialization DTOs, an OkHttp interceptor that adds the
  headers, and `ApiError` mapping (section 7). The base URL comes from settings, so a
  Retrofit instance is built per base URL.
- `data` — Room database (`ProductEntity`, `SpoolEntity`), DAOs, `SyncRepository`,
  `SpoolRepository`, `CatalogRepository`, `SessionStore` (token + server URL + abilities +
  sync cursors).
- `ui` — one package per screen: a `ViewModel` exposing `StateFlow<UiState>` and a
  stateless Composable. Navigation Compose.
- DI: Hilt.

Units talk through interfaces (`FilamentApi`, repositories), so ViewModels can be tested
with fakes and repositories with MockWebServer + in-memory Room.

## 6. Data and sync

- Screens read **only from Room** (Flows). The network updates Room.
- **Spool sync**: `GET /spools?per_page=100[&updated_since=cursor]`, follow `links.next`.
  Upsert each spool. Delete rows whose `deleted_at` is set. The spool's embedded product is
  upserted too. The first sync has no cursor; it also returns no deleted rows, so on the
  first sync the table is replaced.
- **Catalog sync**: `GET /catalog?per_page=100[&updated_since=cursor]`, same paging, upsert.
- **Cursor** = the largest `updated_at` seen in that sync (server time, not the phone clock),
  stored per collection after the whole sync succeeds. If a sync fails halfway, the cursor is
  not advanced (upserts are idempotent, so repeating is safe).
- Sync triggers: app start (when paired), pull to refresh, Sync now, and after every
  successful write. No background work.
- **Writes** go straight to the API. On success, store the returned spool in Room, then run
  an incremental catalog sync so the counts (boxed/on_spool/owned) are right. On a
  network error the write fails with "Can't reach the server"; nothing is queued.
- Concurrency: one sync at a time (Mutex). A request during a running sync joins it.

## 7. Error handling

| Response | Behaviour |
|---|---|
| 401 | Clear the token, go to Pairing with "Your device token is no longer valid — pair again". On `/pair`: "Code invalid, used, expired, or the token limit (10) is reached". |
| 403 JSON (`Invalid ability provided.`) | Treat as read-only: refresh `/me`, hide write actions, show a message. |
| 403 plain text | "This IP address is blocked for 10 minutes after too many failed attempts." |
| 404 | "This spool no longer exists". Remove it from the cache and leave the detail screen. |
| 422 | Forms: show `errors[field]` under the matching field. Actions (open/used-up): show `message`. |
| 429 | "Too many requests — try again in N s" using `Retry-After`. No auto-retry. |
| Network / timeout | Writes: "Can't reach the server at <url>". Sync: offline banner, keep the cache. |
| Other 5xx / parse error | Generic error with the HTTP status; logged without the token. |

## 8. Security

- Token stored encrypted (Android Keystore-backed: EncryptedSharedPreferences or
  Tink-encrypted DataStore). Never logged. The OkHttp logging interceptor is debug-only, with
  `Authorization` redacted.
- Cleartext HTTP allowed through `network_security_config` (`cleartextTrafficPermitted=true`
  base config), because the LAN address varies. Accepted risk for a personal LAN app.
  HTTPS URLs work unchanged.
- Camera permission requested only when the user taps Scan.

## 9. Project setup

- Location: this folder (`F:\Personal Projects\AI\filament app`), `git init`.
- Gradle Kotlin DSL, version catalog (`gradle/libs.versions.toml`), Gradle wrapper, JDK 17
  (Android Studio's bundled JBR if `java` is not on PATH).
- minSdk 26, targetSdk/compileSdk 35. Application id `nl.jaap.filamentspools`.
- Debug build only.

## 10. Testing

- **Unit (JUnit, MockWebServer)**: DTO parsing using the example responses in `openapi.json`;
  error mapping for 401/403 JSON/403 text/404/422/429/network; sync engine (pagination via
  `links.next`, deleted rows removed, cursor advances only on full success, first sync
  replaces the table).
- **Room (Robolectric or instrumented, in-memory)**: on-holder, boxed, running-out and search
  queries.
- **ViewModel (fakes, Turbine)**: open/used-up state changes, read-only hides actions,
  offline banner, 401 → pairing.
- **Manual end-to-end** on the emulator against the local server at `10.0.2.2:8000`:
  pair → sync → open → used up → add → edit → remove → offline (stop the server).

## 11. Update 2026-11-08

- The API dropped `price`, `location` and `notes` from spools (create, PATCH and every response). The app no longer
  shows or edits them; the cache drops the columns (Room version 2, migration keeps cached spools).
- `POST /spools` takes `amount` (1 to 50) to add several identical spools in one call. The app always sends it, so the
  response is always a list; the Add spool form has a "Number of spools" field.
- New event types `weighed` and `used` are labelled in the history.
- Wording: "On the holder" is now "On spool" everywhere in the app.
- Spool lists on the Printer screen are sorted by product code (article number).
