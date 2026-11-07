# Filament Spools Android App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Kotlin/Compose Android companion app that pairs with the Filament Spools server by QR, caches spools and catalog in Room, and lets the user open / use up / add / edit / remove spools from a printer-first home screen.

**Architecture:** Single `:app` module in three packages: `api` (Retrofit + DTOs + error mapping), `data` (Room cache, session store, sync and write repositories) and `ui` (one ViewModel + stateless Composable per screen). Screens read only from Room Flows. Network calls update Room. Hilt wires it together.

**Tech Stack:** Kotlin 2.2.0, AGP 8.11.1, Gradle 8.14.3, Compose BOM 2025.06.01 (Material 3), Navigation Compose 2.9.0, Lifecycle 2.9.1, Hilt 2.56.2 (+ hilt-navigation-compose 1.2.0), KSP 2.2.0-2.0.2, Room 2.7.2, DataStore Preferences 1.1.7, Retrofit 3.0.0 + converter-kotlinx-serialization 3.0.0, OkHttp 4.12.0, kotlinx-serialization-json 1.9.0, kotlinx-coroutines 1.10.2, CameraX 1.4.2, ML Kit barcode-scanning 17.3.0. Tests: JUnit 4.13.2, MockWebServer 4.12.0, Robolectric 4.15.1, Turbine 1.2.1, kotlinx-coroutines-test 1.10.2, androidx.test core 1.6.1.

**Spec:** `docs/superpowers/specs/2026-11-07-filament-spools-android-design.md`

## Global Constraints

- Application id / namespace `nl.jaap.filamentspools`. minSdk 26. compileSdk **36** and targetSdk **36**: android-36 is the only installed platform. This deviates from spec §9, which says 35.
- JVM target 17. Build with Android Studio's JBR: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`. SDK at `%LOCALAPPDATA%\Android\Sdk` (`local.properties` `sdk.dir`, not committed).
- Debug build only. No release signing.
- Every request sends `Accept: application/json`. Authenticated requests also send `Authorization: Bearer <token>`. `POST /pair` sends no token.
- The token is never logged. The HTTP logging interceptor is added only in debug builds, with `Authorization` redacted.
- No automatic retries anywhere (429, 5xx, network).
- Cleartext HTTP is allowed by `res/xml/network_security_config.xml` (`<base-config cleartextTrafficPermitted="true">`).
- Status strings: `boxed`, `on_spool`, `used_up`, `archived`. Owned filter values: `1`, `0`, `boxed`, `on_spool`, `running_out`.
- User-facing copy is fixed by spec §7 (error messages) and §4 (Remove dialog text). Use it verbatim.
- **Design decisions made while planning** (they refine the spec; reviewers should check against these):
  1. **Pagination uses page numbers, not `links.next`.** `links.next` is an absolute URL built from the server's own address (e.g. `http://localhost:8000/...`), which the phone can't reach. Keep requesting `page = n + 1` with the same parameters while `links.next != null`.
  2. **Product counts are derived locally** from the `spools` table: boxed, on_spool, and owned (= boxed + on_spool). The server's `boxed_count`/`on_spool_count`/`owned_count` are ignored. A spool's embedded product has no count fields, and counts must be right immediately after a write without a catalog refetch. This replaces the spec §6 "incremental catalog sync after every write".
  3. **Sync cursor:** the request sends `updated_since = cursor − 1 second`, so that changes made in the same second as the cursor are not missed. Upserts are idempotent, so the overlap is harmless.
  4. **Server URL normalization** (see Task 3) is applied everywhere a URL is entered or read from a QR.

## Review Focus

1. **`links.next` points at an unreachable host.** The server's `APP_URL` is `localhost`, so paging must still work against the LAN IP. Test: `SyncRepositoryTest.followsPagesByNumberNotNextUrl` (Task 6).
2. **A sloppy server URL is typed:** no scheme, trailing slash, missing `/api/v1`, surrounding spaces. Expected: it still works. Test: `ServerUrlTest` (Task 3).
3. **A spool references a product that isn't in the cached catalog.** This happens with inactive products, or when spools sync before the catalog. Expected: no crash and no foreign-key failure, and the spool shows with its embedded product data. Tests: `SpoolDaoTest.spoolWithUnknownProductIsStored` (Task 5) and `SyncRepositoryTest.embeddedProductDoesNotWipeCatalogFields` (Task 6).
4. **The server sends a status, event type or field the app doesn't know.** Expected: parsing succeeds, unknown fields are ignored, and an unknown status is kept as a raw string and shown as-is. Test: `DtoParsingTest.unknownFieldsAndStatusesParse` (Task 2).
5. **Open or Used up is tapped twice quickly.** The second call would answer 422. Expected: the action is disabled while one is in flight for that spool, so only one request is sent. Test: `PrinterViewModelTest.doubleTapSendsOneRequest` (Task 10).

---

## File Structure

```
settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/libs.versions.toml, gradlew(.bat), gradle/wrapper/*
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/res/xml/network_security_config.xml, res/values/strings.xml, themes.xml
app/src/main/java/nl/jaap/filamentspools/
  FilamentApp.kt                     @HiltAndroidApp
  MainActivity.kt                    hosts AppRoot
  api/Dtos.kt                        @Serializable DTOs mirroring openapi schemas
  api/FilamentApi.kt                 Retrofit interface
  api/ApiError.kt                    sealed ApiError + apiCall {} mapping
  api/ServerUrl.kt                   normalize()
  api/ApiProvider.kt                 builds/caches FilamentApi per base URL; auth + 401 interceptors
  data/session/SessionStore.kt       token, server URL, abilities, user, cursors, last sync, signOutReason
  data/session/TokenCipher.kt        interface + KeystoreTokenCipher
  data/db/Entities.kt                ProductEntity, ProductInfo (partial), SpoolEntity
  data/db/Daos.kt                    ProductDao, SpoolDao, query result classes
  data/db/AppDatabase.kt
  data/SyncRepository.kt
  data/SpoolRepository.kt
  data/PairingRepository.kt
  di/AppModule.kt
  ui/AppRoot.kt                      session gate + NavHost + bottom bar
  ui/common/Swatch.kt, ui/common/Messages.kt (ApiError -> user text)
  ui/pairing/{PairingPayload.kt, PairingViewModel.kt, PairingScreen.kt, QrScanner.kt}
  ui/printer/{PrinterViewModel.kt, PrinterScreen.kt}
  ui/catalog/{CatalogViewModel.kt, CatalogScreen.kt, ProductViewModel.kt, ProductScreen.kt}
  ui/spool/{SpoolViewModel.kt, SpoolScreen.kt}
  ui/settings/{SettingsViewModel.kt, SettingsScreen.kt}
app/src/test/java/nl/jaap/filamentspools/...   (mirrors main; fixtures in app/src/test/resources/fixtures/)
```

---

### Task 1: Project scaffold that builds

**Files:**
- Create: `.gitignore`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `local.properties`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/network_security_config.xml`, `app/src/main/res/values/{strings.xml,themes.xml}`, `FilamentApp.kt`, `MainActivity.kt`
- Test: `app/src/test/java/nl/jaap/filamentspools/SmokeTest.kt`

**Interfaces:**
- Produces: a buildable app. `FilamentApp` is annotated `@HiltAndroidApp`. `MainActivity` is `@AndroidEntryPoint` and calls `setContent { AppTheme { Text("Filament Spools") } }`; Task 9 replaces the content with `AppRoot()`.

- [ ] **Step 1: Bootstrap the Gradle wrapper.** Download `https://services.gradle.org/distributions/gradle-8.14.3-bin.zip` into the scratchpad and unzip it. Run `gradle wrapper --gradle-version 8.14.3` in the project root with the JBR `JAVA_HOME`. Expected: `gradlew`, `gradlew.bat` and `gradle/wrapper/` exist.
- [ ] **Step 2: Write the build files.**
  - Put all versions from Tech Stack in `libs.versions.toml`.
  - Plugins: android application, kotlin android, kotlin compose, kotlin serialization, ksp, hilt.
  - Room schema export off.
  - `testOptions.unitTests.isIncludeAndroidResources = true` (for Robolectric).
  - `buildFeatures.compose = true`, `buildConfig = true`.
  - Manifest:
    - permissions `INTERNET`, `CAMERA`; `<uses-feature android:name="android.hardware.camera" android:required="false"/>`
    - `android:networkSecurityConfig="@xml/network_security_config"`
    - `android:name=".FilamentApp"`
  - `.gitignore` covers `.gradle/`, `build/`, `local.properties`, `.idea/`, `*.iml`, `.kotlin/`.
- [ ] **Step 3: Write `SmokeTest`.** `@Test fun arithmetic() = assertEquals(4, 2 + 2)`. This only proves the test pipeline runs.
- [ ] **Step 4: Verify.** Run `./gradlew assembleDebug testDebugUnitTest`. Expected: BUILD SUCCESSFUL; `app/build/outputs/apk/debug/app-debug.apk` exists.
- [ ] **Step 5: Commit.** `git commit -m "chore: scaffold Android project"`

### Task 2: API DTOs and Retrofit interface

**Files:**
- Create: `api/Dtos.kt`, `api/FilamentApi.kt`
- Create fixtures in `app/src/test/resources/fixtures/`. Each one is copied verbatim from the `example` of the matching 200/201 response in `http://localhost:8000/docs/api/openapi.json`:
  - `pair_201.json`
  - `me_200.json`
  - `catalog_200.json`
  - `spools_200.json`
  - `spool_200.json`
  - `events_200.json`
  - `error_422.json` (from the docs' Errors table)
- Test: `api/DtoParsingTest.kt`, plus a `testutil/Fixtures.kt` helper with `fun fixture(name: String): String`

**Interfaces:**
- Produces:
  - `val ApiJson: Json` configured with `ignoreUnknownKeys = true`, `explicitNulls = false`, `coerceInputValues = true`.
  - DTOs, with `@SerialName` snake_case names:
    - `ProductDto(id: Long, brand: String?, material: String?, line: String?, articleName: String, articleNumber: String, colorHex: String?, colorHex2: String?, colorFamily: String?, diameter: Double?, nominalWeight: Int?, hasRefill: Boolean = false, productUrl: String?, buyUrl: String?, updatedAt: String)`. Count fields are deliberately omitted.
    - `SpoolDto(uuid: String, product: ProductDto, status: String, isRefill: Boolean, purchasedAt: String?, price: String?, location: String?, notes: String?, createdAt: String, updatedAt: String, deletedAt: String?)`
    - `SpoolEventDto(type: String, note: String?, createdAt: String)`
    - `Page<T>(data: List<T>, links: PageLinks, meta: PageMeta)`, with `PageLinks(next: String?)` and `PageMeta(currentPage: Int, lastPage: Int)`
    - `Envelope<T>(data: T)`
    - `MeDto(id: Long, name: String, email: String, abilities: List<String>)`
    - `PairedDeviceDto(token: String, abilities: List<String>, expiresAt: String, api: String, user: PairUserDto)`, with `PairUserDto(name: String, email: String)`
    - Request bodies: `PairRequest(code: String, deviceName: String?)`, `NoteRequest(note: String?)`, `CreateSpoolRequest(filamentProductId: Long, status: String, isRefill: Boolean, purchasedAt: String?, price: Double?, location: String?, notes: String?)`
    - `PatchSpoolRequest` is sent as a `JsonObject`, so that only the changed keys go out. Task 7 builds it.
  - `interface FilamentApi` (all `suspend`, relative paths with no leading slash):
    - `pair(@Body PairRequest): Envelope<PairedDeviceDto>`
    - `me(): Envelope<MeDto>`
    - `catalog(@Query("per_page") perPage: Int, @Query("page") page: Int, @Query("updated_since") updatedSince: String?): Page<ProductDto>`
    - `spools(perPage, page, updatedSince): Page<SpoolDto>`, same query parameters as `catalog`
    - `spool(@Path uuid): Envelope<SpoolDto>`
    - `createSpool(@Body CreateSpoolRequest): Envelope<SpoolDto>`
    - `patchSpool(@Path uuid, @Body JsonObject): Envelope<SpoolDto>`
    - `@HTTP(method="DELETE", path="spools/{uuid}", hasBody=true) deleteSpool(@Path uuid, @Body NoteRequest): Response<Unit>`
    - `openSpool(@Path uuid, @Body NoteRequest): Envelope<SpoolDto>`, `POST spools/{uuid}/open`
    - `useUpSpool(@Path uuid, @Body NoteRequest): Envelope<SpoolDto>`, `POST spools/{uuid}/used-up`
    - `events(@Path uuid, @Query("page") page: Int): Page<SpoolEventDto>`

- [ ] **Step 1: Write failing tests** in `DtoParsingTest`:
  - `pairParses`: `token == "7|kq3J1nVb0pXr8eLw2YtZ5aGc9hDf4sMu6iOyTlNe"`, `abilities == listOf("spools:read","spools:write")`, `api == "http://localhost:8000/api/v1"`.
  - `spoolsPageParses`: one spool, `uuid == "9d3c6f1e-4b7a-4c2e-9f1d-2a8b5e7c0d43"`, `status == "on_spool"`, `price == "24.99"`, `product.colorHex == "#FFFFFF"`, `links.next == null`.
  - `catalogPageParses`: `articleNumber == "10100"`, `buyUrl` set, `hasRefill == true`.
  - `eventsParse`: two events, second `note == "X1C AMS slot 2"`.
  - `unknownFieldsAndStatusesParse`: take the spools fixture, add `"extra":1` and set `"status":"lent_out"`. Parsing succeeds and `status == "lent_out"`.
  - `createRequestSerializesSnakeCase`: encoding `CreateSpoolRequest(1,"boxed",false,null,null,null,null)` contains `"filament_product_id":1` and contains no `"price"` key.
- [ ] **Step 2: Run.** `./gradlew testDebugUnitTest --tests "*DtoParsingTest"`. Expected: compile FAIL (types missing).
- [ ] **Step 3: Implement** `Dtos.kt` and `FilamentApi.kt` with the signatures above.
- [ ] **Step 4: Run the same command.** Expected: PASS.
- [ ] **Step 5: Commit.** `git commit -m "feat(api): DTOs and Retrofit interface"`

### Task 3: Error mapping, server URL, ApiProvider

**Files:**
- Create: `api/ApiError.kt`, `api/ServerUrl.kt`, `api/ApiProvider.kt`
- Test: `api/ApiErrorTest.kt`, `api/ServerUrlTest.kt`, `api/ApiProviderTest.kt`

**Interfaces:**
- Consumes: `FilamentApi`, `ApiJson` (Task 2).
- Produces:
  - `sealed class ApiError : Exception()` with these subclasses:
    - `Unauthorized`
    - `MissingAbility`, for a 403 with a JSON body
    - `IpBlocked`, for a 403 that isn't JSON
    - `NotFound`
    - `Validation(message: String, fields: Map<String, List<String>>)`
    - `RateLimited(retryAfterSeconds: Int?)`
    - `Network(cause: IOException)`
    - `Http(code: Int)`, for other statuses and parse failures
  - `suspend fun <T> apiCall(block: suspend () -> T): Result<T>`. It catches `HttpException` and `IOException`; `SerializationException` becomes `Http(-1)`.
  - `object ServerUrl { fun normalize(input: String): String }`. It returns a base URL that always ends in `/api/v1/` and throws `IllegalArgumentException` for blank or unparsable input. Rules:
    - trim the input
    - add `http://` when there's no scheme
    - strip trailing slashes
    - append `/api/v1` unless the path already ends with it
    - append `/`
  - `class ApiProvider @Inject constructor(session: SessionStore)`
    - `fun api(): FilamentApi` uses the current session URL and token; it is cached and rebuilt when the URL changes.
    - `fun unauthenticated(baseUrl: String): FilamentApi` is used for pairing.
    - OkHttp interceptors add `Accept` (always) and `Authorization` (only when a token is set).
    - A 401 on any authenticated call calls `session.signOut(SignOutReason.TokenInvalid)`.
    - Timeouts: 10 s connect, 20 s read.
  - `SessionStore` is defined in Task 4. Here, depend on the interface `SessionStore` with `val current: StateFlow<Session?>` and `suspend fun signOut(reason: SignOutReason)`. Put that interface plus `data class Session(baseUrl: String, token: String, abilities: Set<String>, userName: String)` and `enum class SignOutReason { User, TokenInvalid }` in `data/session/SessionStore.kt` now. Task 4 adds the implementation.

- [ ] **Step 1: Write failing tests.**
  - **`ServerUrlTest`:**
    - `"192.168.1.5:8000"` → `"http://192.168.1.5:8000/api/v1/"`
    - `" http://10.0.2.2:8000/api/v1/ "` → `"http://10.0.2.2:8000/api/v1/"`
    - `"https://spools.example.com"` → `"https://spools.example.com/api/v1/"`
    - `"http://host/api/v1"` → `"http://host/api/v1/"`
    - `""` throws
  - **`ApiErrorTest`** (MockWebServer; call through a real `FilamentApi` built on the server URL):
    - 401 → `Unauthorized`
    - 403 with body `{"message":"Invalid ability provided."}` and JSON content type → `MissingAbility`
    - 403 with body `Blocked` as `text/plain` → `IpBlocked`
    - 404 → `NotFound`
    - 422 with `error_422.json` → `Validation`, with `fields["filament_product_id"] == listOf("The filament product id field is required.")`
    - 429 with `Retry-After: 42` → `RateLimited(42)`
    - shutting the server down → `Network`
    - 500 → `Http(500)`
  - **`ApiProviderTest`** (fake `SessionStore`):
    - a request carries `Authorization: Bearer t0k` and `Accept: application/json`
    - `unauthenticated()` sends no `Authorization` header
    - a 401 from `me()` triggers `signOut(TokenInvalid)` exactly once
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*.api.*"`. Expected: FAIL.
- [ ] **Step 3: Implement.** The 403 split is decided by the response `Content-Type` containing `json`.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit.** `git commit -m "feat(api): error mapping, URL normalization, client provider"`

### Task 4: Session store with encrypted token

**Files:**
- Create: `data/session/TokenCipher.kt`, `data/session/DataStoreSessionStore.kt`
- Modify: `data/session/SessionStore.kt`, adding the members below to the interface
- Test: `data/session/SessionStoreTest.kt` (Robolectric, using a temp DataStore file and a fake cipher)

**Interfaces:**
- Produces:
  - `interface TokenCipher { fun encrypt(plain: String): String; fun decrypt(cipher: String): String }`
  - `KeystoreTokenCipher`: AES/GCM with a key in `AndroidKeyStore` under alias `filament_token`. The output is Base64 of IV + ciphertext.
  - `SessionStore` adds:
    - `suspend fun signIn(session: Session)`
    - `suspend fun updateAbilities(abilities: Set<String>)`
    - `val signOutReason: StateFlow<SignOutReason?>`
    - `suspend fun clearSignOutReason()`
    - `suspend fun cursor(key: String): String?`
    - `suspend fun setCursor(key: String, value: String?)`
    - `val lastSync: StateFlow<Instant?>`
    - `suspend fun setLastSync(at: Instant)`
    - `val canWrite: Boolean get() = "spools:write" in (current.value?.abilities ?: emptySet())`
  - `signOut` clears the token and cursors and sets the reason. Clearing Room is not done here; Task 9 does it.
  - Cursor keys: `"spools"`, `"catalog"`.

- [ ] **Step 1: Write failing tests:**
  - `signInRoundTrips`: after `signIn`, a new store instance on the same file has `current.value` equal to the same `Session`.
  - `tokenIsStoredEncrypted`: the raw DataStore file text does not contain the token. The fake cipher reverses the string with a prefix.
  - `signOutClearsTokenAndCursors`: `current.value == null`, `cursor("spools") == null`, `signOutReason.value == TokenInvalid`.
  - `canWriteReflectsAbilities`.
- [ ] **Step 2: Run** `--tests "*SessionStoreTest"`. Expected: FAIL.
- [ ] **Step 3: Implement** with DataStore Preferences. `current` is hydrated with `stateIn` on an injected application `CoroutineScope`.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit.** `git commit -m "feat(data): encrypted session store"`

### Task 5: Room cache and queries

**Files:**
- Create: `data/db/Entities.kt`, `data/db/Daos.kt`, `data/db/AppDatabase.kt`
- Test: `data/db/SpoolDaoTest.kt`, `data/db/ProductDaoTest.kt` (Robolectric, in-memory DB)

**Interfaces:**
- Produces:
  - `ProductEntity(id PK, brand, material, line, articleName, articleNumber, colorHex, colorHex2, colorFamily, diameter, nominalWeight, hasRefill, productUrl, buyUrl, updatedAt)`
  - `SpoolEntity(uuid PK, productId (indexed, **no foreign key**), status, isRefill, purchasedAt, price: String?, location, notes, createdAt, updatedAt)`
  - `fun ProductDto.toEntity()` and `fun SpoolDto.toEntity()`, kept in `Entities.kt`.
  - Result rows:
    - `SpoolWithProduct(@Embedded spool, @Relation product: ProductEntity?)`
    - `ProductWithCounts(@Embedded product, boxed: Int, onSpool: Int)` with `owned = boxed + onSpool`
  - `ProductDao`:
    - `upsert(List<ProductEntity>)`
    - `observeFiltered(q: String, material: String?, brand: String?, line: String?, color: String?, owned: OwnedFilter): Flow<List<ProductWithCounts>>`, ordered by `articleNumber`
    - `observe(id): Flow<ProductWithCounts?>`
    - `observeRunningOut(): Flow<List<ProductWithCounts>>`
    - `distinctMaterials/Brands/Lines/Colors(): Flow<List<String>>`
    - `clear()`
  - `enum class OwnedFilter { All, Owned, NotOwned, Boxed, OnSpool, RunningOut }`
  - Counts come from a `LEFT JOIN` on spools grouped by product, with `SUM(status='boxed')` and `SUM(status='on_spool')`.
  - `SpoolDao`:
    - `upsert(List<SpoolEntity>)`
    - `deleteByUuids(List<String>)`
    - `replaceAll(List<SpoolEntity>)` (`@Transaction`)
    - `observeByStatus(status: String, q: String): Flow<List<SpoolWithProduct>>`, newest `updatedAt` first
    - `observe(uuid): Flow<SpoolWithProduct?>`
    - `observeForProduct(productId): Flow<List<SpoolWithProduct>>`
    - `clear()`
  - Search `q` matches when it's blank, or when it occurs case-insensitively in `articleName`, `articleNumber`, `colorFamily`, `line` or `material`.
  - `AppDatabase` version 1, `exportSchema = false`.

- [ ] **Step 1: Write failing tests:**
  - `SpoolDaoTest.observeByStatusFiltersAndSearches`: seed a boxed "Jade White", a boxed "Bambu Green" and an on_spool "Jade White". `("boxed","jade")` returns 1 spool; `("boxed","")` returns 2.
  - `SpoolDaoTest.spoolWithUnknownProductIsStored`: inserting a spool with productId 999 and no product works; the result has `product == null`.
  - `SpoolDaoTest.replaceAllDropsMissing`.
  - `ProductDaoTest.countsDerivedFromSpools`: product 1 has 2 boxed, 1 on_spool and 1 used_up spool → `boxed == 2`, `onSpool == 1`, `owned == 3`.
  - `ProductDaoTest.runningOutMeansOnSpoolWithoutBoxed`: product A has on_spool only (included). B has on_spool and boxed (excluded). C has nothing (excluded).
  - `ProductDaoTest.ownedFilters`: `NotOwned` returns only C; `Owned` returns A and B.
- [ ] **Step 2: Run** `--tests "*.db.*"`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit.** `git commit -m "feat(data): Room cache with derived counts"`

### Task 6: Sync engine

**Files:**
- Create: `data/SyncRepository.kt`
- Test: `data/SyncRepositoryTest.kt` (MockWebServer + in-memory Room + fake SessionStore)

**Interfaces:**
- Consumes: `ApiProvider.api()`, `apiCall`, the DAOs, `SessionStore.cursor/setCursor/setLastSync`.
- Produces:
  - `class SyncRepository`
    - `suspend fun sync(): Result<Unit>` runs spools, then catalog.
    - `val state: StateFlow<SyncState>`, where `data class SyncState(running: Boolean, lastError: ApiError?)`.
  - Algorithm per collection:
    - `since = cursor?.let { Instant.parse(it).minusSeconds(1).toString() }`
    - Start at page 1 with `per_page = 100`.
    - Loop while `links.next != null`, with `page++`.
    - For spools, collect every row:
      - rows with `deletedAt != null` go to `deleteByUuids`
      - the others are upserted
      - each row's embedded product is upserted with `insert-or-ignore`, so catalog fields from `/catalog` are never overwritten by the embedded copy
    - On a first sync (`cursor == null`), the spool rows go through `replaceAll` instead.
    - After every page has succeeded, set the cursor to the max `updatedAt` seen (unchanged if no rows came back) and call `setLastSync(now)`.
    - If any page fails, nothing is written for that collection and the cursor isn't advanced.
  - A `Mutex` makes concurrent `sync()` calls run once: the second caller waits and gets the same result.

- [ ] **Step 1: Write failing tests:**
  - `followsPagesByNumberNotNextUrl`: page 1 has `links.next = "http://unreachable.invalid/api/v1/spools?page=2"`; page 2 has `next = null`. Assert the second recorded request path is `/api/v1/spools?per_page=100&page=2` on the MockWebServer, and both spools are stored.
  - `deletedRowsAreRemoved`: an existing spool row plus an incremental response that returns it with `deleted_at` set → the row is gone.
  - `firstSyncReplacesTable`: a stale local spool that's absent from the full response is removed.
  - `cursorSentMinusOneSecond`: with cursor `2026-11-01T09:30:00Z`, the request has `updated_since=2026-11-01T09:29:59Z` (URL-encoded).
  - `cursorNotAdvancedOnFailure`: page 2 returns 500 → the cursor is unchanged and the result is `Http(500)`.
  - `embeddedProductDoesNotWipeCatalogFields`: the product synced from catalog has `buyUrl = X`; a spool whose embedded product has `buyUrl = null` syncs → `buyUrl` is still X.
  - `concurrentSyncRunsOnce`: two parallel `sync()` calls → requests counted once per page.
- [ ] **Step 2: Run** `--tests "*SyncRepositoryTest"`. Expected: FAIL.
- [ ] **Step 3: Implement.** For insert-or-ignore, add `@Insert(onConflict = IGNORE) insertIgnore(List<ProductEntity>)` to `ProductDao`.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit.** `git commit -m "feat(data): incremental sync engine"`

### Task 7: Spool write repository

**Files:**
- Create: `data/SpoolRepository.kt`
- Test: `data/SpoolRepositoryTest.kt`

**Interfaces:**
- Consumes: `ApiProvider`, `apiCall`, `SpoolDao`, `ProductDao.insertIgnore`, `SessionStore.updateAbilities`.
- Produces `class SpoolRepository`, where every method returns `Result<…>`:
  - `open(uuid, note: String? = null): Result<Unit>`
  - `useUp(uuid, note: String? = null): Result<Unit>`
  - `add(productId: Long, onSpool: Boolean, isRefill: Boolean, purchasedAt: LocalDate?, price: BigDecimal?, location: String?, notes: String?): Result<String>`, which returns the new uuid
  - `update(uuid, changes: SpoolChanges): Result<Unit>`
    - `sealed interface Change<out T> { data object Unchanged : Change<Nothing>; data class Set<T>(val value: T?) : Change<T> }`
    - `data class SpoolChanges(isRefill: Change<Boolean> = Unchanged, purchasedAt: Change<LocalDate> = Unchanged, price: Change<BigDecimal> = Unchanged, location: Change<String> = Unchanged, notes: Change<String> = Unchanged)`
    - An `Unchanged` key is left out of the JSON body. `Set(null)` sends an explicit JSON `null`.
  - `remove(uuid, note: String?): Result<Unit>`
  - `events(uuid, page: Int): Result<Page<SpoolEventDto>>`
  - `refreshAbilities(): Result<Unit>`
  - Side effects:
    - A returned spool is upserted into Room, and its product is inserted with insert-or-ignore.
    - `remove` success, or `NotFound` from any call, deletes the row locally.
    - `MissingAbility` calls `refreshAbilities()` (via `me()` → `updateAbilities`) before returning the failure.

- [ ] **Step 1: Write failing tests:**
  - `openUpdatesCache`: the cached spool is boxed; the server returns status `on_spool` → the Room row status is `on_spool`.
  - `removeDeletesLocally`: DELETE gets 204 → the row is gone, and the request body is `{"note":"sold"}`.
  - `notFoundDeletesLocally`: `useUp` gets 404 → the row is gone and the result is `NotFound`.
  - `missingAbilityRefreshesAbilities`: `open` gets 403 JSON and the follow-up `/me` returns `["spools:read"]` → `session.canWrite == false`.
  - `patchSendsOnlyChangedFields`: `SpoolChanges(location = Set("Shelf B"), notes = Set(null))` sends a body equal to `{"location":"Shelf B","notes":null}`.
  - `addSendsSnakeCaseAndStatus`: with `onSpool = true` the body has `"status":"on_spool"` and `"price":24.99`.
- [ ] **Step 2: Run** `--tests "*SpoolRepositoryTest"`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit.** `git commit -m "feat(data): spool write repository"`

### Task 8: Pairing (payload parsing, repository, screen, QR scanner)

**Files:**
- Create: `ui/pairing/PairingPayload.kt`, `data/PairingRepository.kt`, `ui/pairing/PairingViewModel.kt`, `ui/pairing/PairingScreen.kt`, `ui/pairing/QrScanner.kt`
- Test: `ui/pairing/PairingPayloadTest.kt`, `ui/pairing/PairingViewModelTest.kt`

**Interfaces:**
- Consumes: `ApiProvider.unauthenticated`, `ServerUrl.normalize`, `SessionStore.signIn`, `SyncRepository.sync`.
- Produces:
  - `data class PairingPayload(api: String, code: String, expiresAt: Instant?)`
  - `object PairingPayloadParser { fun parse(text: String): PairingPayload? }`, which accepts only JSON with `"type":"filament-spools-pairing"`.
  - `class PairingRepository { suspend fun pair(serverUrl: String, code: String, deviceName: String): Result<Unit> }`. It normalizes the URL, calls `POST pair`, then `signIn(Session(normalizedUrl, token, abilities, user.name))`, then `sync()`. A sync failure does not fail pairing.
  - **Use the URL the user entered, not `PairedDeviceDto.api`**, because that may be `localhost`.
  - `PairingViewModel`:
    - `state: StateFlow<PairingUiState(serverUrl, code, deviceName, expiresAt, busy, error: String?)>`
    - methods `onScanned(text)`, `onServerUrl(..)`, `onCode(..)`, `onDeviceName(..)`, `submit()`
    - `deviceName` defaults to `Build.MODEL`, injected as a `String` for tests
  - Error text:
    - 401 → "Code invalid, used, expired, or the token limit (10) is reached"
    - other errors → `Messages.text(error, serverUrl)` (from `ui/common/Messages.kt`, created in this task; it maps every `ApiError` to the spec §7 copy, and `Network` becomes "Can't reach the server at <serverUrl>")
  - `QrScanner` Composable: CameraX `PreviewView` plus an ML Kit `BarcodeScanning` analyzer with the `FORMAT_QR_CODE` option. It calls `onResult(text)` once. The camera permission is requested via `rememberLauncherForActivityResult` only when Scan is tapped. The screen also has a "Paste QR text" field and manual URL/code fields.

- [ ] **Step 1: Write failing tests:**
  - `PairingPayloadTest`:
    - a valid JSON payload parses
    - a wrong `type` → null
    - non-JSON → null
    - a missing `expires_at` → `expiresAt == null`
  - `PairingViewModelTest` (fake repository):
    - `scanFillsFields`: scanning a payload with `api = "http://localhost:8000/api/v1"` sets the `serverUrl` field to that value. The user can then edit it.
    - `expiredCodeShowsWarning`: `expiresAt` in the past → `error == "This code has expired — show a new QR on the Devices page"`, and `submit` doesn't call the repository.
    - `unauthorizedShowsPairCopy`: the repository returns `Unauthorized` → the pairing 401 copy above.
    - `busyWhileSubmitting`.
- [ ] **Step 2: Run** `--tests "*.pairing.*"`. Expected: FAIL.
- [ ] **Step 3: Implement.** Add a `PairingRepositoryTest` against MockWebServer asserting that the stored session `baseUrl` is the normalized user URL even when the response `api` is `localhost`.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit.** `git commit -m "feat(pairing): QR pairing flow"`

### Task 9: App shell, navigation, Settings

**Files:**
- Create: `ui/AppRoot.kt`, `ui/AppViewModel.kt`, `ui/settings/SettingsViewModel.kt`, `ui/settings/SettingsScreen.kt`, `di/AppModule.kt`
- Modify: `MainActivity.kt` (`setContent { AppTheme { AppRoot() } }`)
- Test: `ui/AppViewModelTest.kt`, `ui/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `SessionStore`, `SyncRepository`, the DAOs.
- Produces:
  - `AppViewModel`:
    - `state: StateFlow<AppState>`, where `AppState` is `Loading | NeedsPairing(message: String?) | Ready`
    - Whenever the session becomes null, it clears both DAOs.
    - On `Ready` it triggers `sync()` once.
    - The `TokenInvalid` message is "Your device token is no longer valid — pair again".
  - Routes: `printer`, `catalog`, `settings`, `product/{id}`, `spool/{uuid}`. A bottom bar shows on the first three.
  - `SettingsViewModel`:
    - exposes `serverUrl`, `userName`, `tokenType` ("Read-only" / "Read-write"), `lastSync` and `syncing`
    - `syncNow()`
    - `signOut()`, which calls `session.signOut(User)`
    - `changeServer()`, which signs out after the screen confirms
  - The Settings screen shows: "Signing out removes the token from this phone only. Revoke it on the Devices page to disable it."
  - `AppModule` provides the `AppDatabase` singleton, the DAOs, the `SessionStore` binding, `KeystoreTokenCipher`, an application `CoroutineScope`, and `@Named("deviceName") Build.MODEL`.

- [ ] **Step 1: Write failing tests:**
  - `AppViewModelTest`:
    - `noSessionNeedsPairing`
    - `tokenInvalidShowsMessageAndClearsCache`: the DAOs are empty after the transition
    - `readyTriggersSyncOnce`
  - `SettingsViewModelTest`:
    - `tokenTypeReadOnly` when the abilities are `{spools:read}`
    - `signOutUsesUserReason`
- [ ] **Step 2: Run** `--tests "*AppViewModelTest" --tests "*SettingsViewModelTest"`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run all tests and assemble.** `./gradlew testDebugUnitTest assembleDebug`. Expected: PASS and BUILD SUCCESSFUL.
- [ ] **Step 5: Commit.** `git commit -m "feat(ui): app shell, navigation, settings"`

### Task 10: Printer home screen

**Files:**
- Create: `ui/printer/PrinterViewModel.kt`, `ui/printer/PrinterScreen.kt`, `ui/common/Swatch.kt`
- Test: `ui/printer/PrinterViewModelTest.kt`

**Interfaces:**
- Consumes:
  - `SpoolDao.observeByStatus("on_spool"|"boxed", q)`
  - `ProductDao.observeRunningOut()`
  - `SpoolRepository.open/useUp`
  - `SyncRepository.sync/state`
  - `SessionStore.canWrite/lastSync`
- Produces:
  - `PrinterUiState(query, onHolder: List<SpoolWithProduct>, runningOut: List<ProductWithCounts>, boxed: List<SpoolWithProduct>, canWrite: Boolean, busyUuids: Set<String>, offlineSince: Instant?, refreshing: Boolean)`
  - `offlineSince = lastSync` when `sync.state.lastError is ApiError.Network`; otherwise null.
  - `runningOut` is filtered by the same query, in memory.
  - Methods:
    - `onQuery(String)`
    - `refresh()`
    - `open(uuid)`
    - `useUp(uuid)`, called after the screen's confirm dialog
  - `val messages: SharedFlow<String>` carries snackbars: "Put on the holder" and "Marked used up", or `Messages.text(error, serverUrl)`.
  - `open`/`useUp` ignore calls for a uuid already in `busyUuids`.
  - `Swatch(colorHex: String?, colorHex2: String?)` draws a circle, split diagonally when there are two colors. A null or invalid hex draws a grey circle outlined with a "?".
  - Screen layout: search field, then the sections "On the holder", "Running out" (buy-link button opens `buyUrl` with `ACTION_VIEW`) and "Boxed". Pull to refresh. The offline banner reads "Offline — last synced HH:mm". Write buttons are hidden when `!canWrite`. Tapping a card navigates to `spool/{uuid}`.

- [ ] **Step 1: Write failing tests** (fake DAOs or in-memory Room, fake repository):
  - `doubleTapSendsOneRequest`: the repository's `open` suspends on a `CompletableDeferred`; calling `open("u1")` twice gives a repository call count of 1.
  - `queryFiltersSections`: the query "green" narrows `boxed` and `runningOut`.
  - `readOnlyHidesActions`: `canWrite == false`.
  - `networkErrorShowsOfflineBanner`: the sync state's `lastError = Network`, with `lastSync` set → `offlineSince == lastSync`.
  - `useUpFailureEmitsMessage`: `RateLimited(42)` → the message is "Too many requests — try again in 42 s".
- [ ] **Step 2: Run** `--tests "*PrinterViewModelTest"`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit.** `git commit -m "feat(ui): printer home screen"`

### Task 11: Catalog, product detail and Add spool

**Files:**
- Create: `ui/catalog/CatalogViewModel.kt`, `CatalogScreen.kt`, `ProductViewModel.kt`, `ProductScreen.kt`
- Test: `ui/catalog/CatalogViewModelTest.kt`, `ui/catalog/ProductViewModelTest.kt`

**Interfaces:**
- Consumes: `ProductDao.observeFiltered/observe/distinct*`, `SpoolDao.observeForProduct`, `SpoolRepository.add`.
- Produces:
  - `CatalogViewModel`:
    - `state(query, filters: CatalogFilters(material, brand, line, color, owned: OwnedFilter), products, options: FilterOptions(materials, brands, lines, colors))`
    - `onQuery`, `onFilters`
  - `ProductViewModel(savedStateHandle["id"])`:
    - `state(product: ProductWithCounts?, spools, form: AddSpoolForm, fieldErrors: Map<String,String>, saving, canWrite)`
    - `AddSpoolForm(onSpool=false, isRefill=false, purchasedAt="", price="", location="", notes="")`
    - `onForm(AddSpoolForm)`, `save()`
  - `save()` validates locally first:
    - `purchasedAt` blank or `YYYY-MM-DD`; otherwise the error on `purchased_at` is "Use YYYY-MM-DD"
    - `price` blank or a non-negative decimal; otherwise the error on `price` is "Enter a number"
  - Server 422 `fields` map onto the form keys: the first message per field.
  - On success, emit "Spool added" and reset the form.
  - The refill switch shows only when `product.hasRefill`. A blank string becomes `null` in the request.

- [ ] **Step 1: Write failing tests:**
  - `CatalogViewModelTest.filtersPassedToDao`: `owned = RunningOut` with `material = "PLA"` reaches the DAO query.
  - `ProductViewModelTest`:
    - `invalidDateBlocksSave`: no repository call, and `fieldErrors["purchased_at"] == "Use YYYY-MM-DD"`
    - `serverValidationMapsToFields`: the repository returns `Validation` with `{"location":["Too long."]}` → `fieldErrors["location"] == "Too long."`
    - `successResetsForm`
    - `refillHiddenWhenProductHasNoRefill`
- [ ] **Step 2: Run** `--tests "*.catalog.*"`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit.** `git commit -m "feat(ui): catalog and add spool"`

### Task 12: Spool detail, edit, remove, history

**Files:**
- Create: `ui/spool/SpoolViewModel.kt`, `ui/spool/SpoolScreen.kt`
- Test: `ui/spool/SpoolViewModelTest.kt`

**Interfaces:**
- Consumes: `SpoolDao.observe`, `SpoolRepository.open/useUp/update/remove/events`.
- Produces:
  - `SpoolViewModel(savedStateHandle["uuid"])`:
    - `state(spool: SpoolWithProduct?, editing: EditForm?, fieldErrors, events: List<SpoolEventDto>, eventsError: String?, hasMoreEvents, busy, canWrite, gone: Boolean)`
    - `startEdit()`, `onEdit(EditForm)`, `saveEdit()`
    - `open()`, `useUp()`, `remove(note)`, `loadMoreEvents()`
  - `saveEdit()` sends only the fields that differ from the cached spool, using `SpoolChanges`. If nothing changed, it closes the editor without a request.
  - `gone = true` when the cached row disappears (after a remove or a `NotFound`). The screen then pops back and shows "This spool no longer exists" (on `NotFound`) or "Spool removed".
  - Open shows only for `boxed`, Used up for `boxed`/`on_spool`, and Remove for any status.
  - Remove dialog copy: "Remove this spool? Use this for mistakes or spools sold/given away — it is not counted as used." with an optional note field.
  - Events load page 1 on init and append pages while `links.next != null`. On a network error, `eventsError` is "History needs a connection to the server".

- [ ] **Step 1: Write failing tests:**
  - `editSendsOnlyChangedFields`: the cached location is "Shelf A"; the edit changes the location to "Shelf B" → the repository gets `SpoolChanges(location = Set("Shelf B"))` with everything else `Unchanged`.
  - `noChangesNoRequest`.
  - `actionsByStatus`: `used_up` → neither open nor useUp is available.
  - `notFoundMarksGone`.
  - `eventsOfflineShowsMessage`.
- [ ] **Step 2: Run** `--tests "*SpoolViewModelTest"`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run all tests and assemble.** `./gradlew testDebugUnitTest assembleDebug`. Expected: PASS and BUILD SUCCESSFUL.
- [ ] **Step 5: Commit.** `git commit -m "feat(ui): spool detail, edit, remove, history"`

### Task 13: End-to-end check on the emulator

**Files:** none (fix-forward commits only if bugs are found)

- [ ] **Step 1: Start the server** reachable from the emulator: the Laravel app at `localhost:8000` (the emulator reaches the host as `10.0.2.2`). Start an AVD and install the app: `./gradlew installDebug`.
- [ ] **Step 2: Pair.** On the Devices page, Show QR code. In the app, use Paste QR text with the QR JSON (the emulator camera can't scan easily), then edit the URL to `http://10.0.2.2:8000/api/v1`. Expected: lands on Printer, with spools listed.
- [ ] **Step 3: Exercise the actions.** Open a boxed spool → it moves to "On the holder", and the web app shows the opened event. Then Used up → confirm. Add a spool from the catalog. Edit the location. Remove with a note. Expected: each change is visible in the web app and in the app's lists.
- [ ] **Step 4: Offline.** Stop the server and pull to refresh. Expected: the offline banner shows, the lists stay, and an Open attempt shows "Can't reach the server at http://10.0.2.2:8000/api/v1/".
- [ ] **Step 5: Revoke.** Revoke the token on the Devices page, then refresh. Expected: back to Pairing with "Your device token is no longer valid — pair again".
- [ ] **Step 6:** Report the results. Commit any fixes, each with its regression test.
