# SMACian Backend — Deep Technical Audit

> Stack: Spring Boot 3.3.5 + Kotlin 2.0.21 + Hibernate 6 + PostgreSQL | Scale assumption: ~500 users growing to several thousand
> Audit date: 2026-10-04 | Method: full source read (75 files) + 3 parallel deep-dives with `file:line` evidence

---

## Table of contents

1. [What I Understand About Your Current Architecture](#1-what-i-understand-about-your-current-architecture)
2. [Request/Data Flow I Found](#2-requestdata-flow-i-found)
3. [Problems and Bottlenecks](#3-problems-and-bottlenecks)
4. [Recommended Improvements](#4-recommended-improvements)
5. [Implementation Roadmap](#5-implementation-roadmap)
6. [Performance targets](#6-performance-targets-postphase-1)
7. [Appendix A — Current architecture diagram](#appendix-a--current-architecture)
8. [Appendix B — Endpoint inventory (30 endpoints)](#appendix-b--endpoint-inventory-30-endpoints)
9. [Appendix C — What was explicitly NOT recommended (and why)](#appendix-c--what-was-explicitly-not-recommended-and-why)
10. [Appendix D — Items that could not be verified from code](#appendix-d--items-that-could-not-be-verified-from-code)

---

## 1. What I Understand About Your Current Architecture

**You run a classic layered monolith — Controller → Service → Repository → PostgreSQL — with no exotic patterns.** Specifically:

- **Layering:** `controller/` (30 endpoints across 5 controllers) → `service/` (AuthService, UserService, OtpService, NewsfeedService, EmailService, CloudinaryService) → `repository/` (8 Spring Data JPA interfaces) → PostgreSQL via Hibernate 6. Package-by-layer, not by feature. Consistent throughout — no Clean Architecture, no DDD aggregates, no CQRS, no events. That is appropriate for your scale.
- **DTO pattern with hand-rolled factories:** every response is a Kotlin `data class` with a `fromEntity()` companion (e.g. `PostResponse.fromEntity`, `UserResponse.fromEntity`). No MapStruct/ModelMapper. Request DTOs use `jakarta.validation` annotations on auth paths, manual validation elsewhere (`ProfileValidation` object, inline checks in services).
- **Relationship style is unusual but deliberate:** all associations are **unidirectional `@ManyToOne(fetch = LAZY)` from child → parent**. There is not a single `@OneToMany`, `mappedBy`, `cascade=`, or `orphanRemoval` in the codebase (verified by grep — 0 hits). Delete propagation is **DB-level only** via Hibernate `@OnDelete(CASCADE)`. This avoids the entire class of bidirectional/JPA-cascade bugs — a good choice — at the cost that every "children of X" read is an explicit repository query (which is where the N+1s come from; see §3).
- **Stateless JWT auth:** `JwtAuthenticationFilter` → `SecurityConfig` (stateless, CSRF off) → `CurrentUser.getUserId()` (ID extracted from token `sub`). Controllers never take a user ID from the request — IDOR-safe by construction. No roles (everyone is `ROLE_USER`), no refresh tokens, no revocation list.
- **Image storage is "BYTEA + self-streaming":** profile/cover/post images live in PostgreSQL `@Lob` columns and stream back through public `GET` endpoints (`/api/user/profile/photo/{id}`, `/api/feed/images/{id}`, permitAll because mobile `<Image>` sends no JWT). Cloudinary upload code is dead; only a `ui-avatars.com` URL builder still uses `CloudinaryService`.
- **Error model is consistent:** `RuntimeException` subclasses → `GlobalExceptionHandler` → `{success:false, timestamp, status, error, message, path, fieldErrors?}` JSON. No stack traces leak.

**In one sentence:** a well-organized, conventional Spring Boot monolith with clean layering, honest trade-offs, and performance problems concentrated in exactly two places (feed reads, BYTEA handling) plus a handful of security-hygiene gaps.

---

## 2. Request/Data Flow I Found

### 2.1 Feed read — `GET /api/feed` (most expensive endpoint)

`NewsfeedController.getFeed` (`controller/NewsfeedController.kt:80-85`) → `NewsfeedService.getFeed` (`service/NewsfeedService.kt:108-125`, `@Transactional(readOnly=true)`) → `PostRepository.findAllByOrderByCreatedAtDesc` (1 page `SELECT` + 1 `COUNT(*)`) → per post, `toPostResponse` (`service/NewsfeedService.kt:202-211`) runs **5 statements**:

| # | Query | Source |
|---|-------|--------|
| A | `SELECT * FROM post_images WHERE post_id=?` (**loads full BYTEA bytes**, only `id` is used) | `service/NewsfeedService.kt:229-237` |
| B | `SELECT COUNT(*) FROM post_likes WHERE post_id=?` | `repository/PostLikeRepository.kt:23` |
| C | `SELECT COUNT(*) FROM comments WHERE post_id=?` | `repository/CommentRepository.kt:24` |
| D | `SELECT 1 ... WHERE post_id=? AND user_id=?` (liked check) | `repository/PostLikeRepository.kt:17` |
| E | `SELECT * FROM users WHERE id=?` (lazy `author` proxy touched by `PostResponse.fromEntity` at `dto/response/PostResponse.kt:42-48`) | `entity/Post.kt:24` LAZY + open-in-view=false, resolved in-tx |

**Full page of 20 = 2 + 20×5 = 102 SQL statements.** `GET /mine` and `GET /api/feed/{id}` (6 statements) share the same shape. `open-in-view=false` (`application.properties:40`) is correctly set — nothing here throws `LazyInitializationException`, it just costs queries.

### 2.2 Photo upload → display

`POST /api/user/profile/photo` (multipart `file`) → `UserService.updateProfilePhoto` (`service/UserService.kt:128-143`, `@Transactional`): empty-check → type+5 MB check → `file.bytes` into `profilePhotoData` → `photoStreamUrl` builds absolute URL via `ServletUriComponentsBuilder.fromCurrentContextPath()` (trusts `X-Forwarded-*` per `server.forward-headers-strategy=framework`) → `save` → `buildUserResponse` (+2 child `SELECT`s). **4 statements + full file in heap inside the transaction.** Display is `GET /api/user/profile/photo/{userId}` → `getProfilePhotoStream` (1 `SELECT` incl. BYTEA) → whole `ByteArray` as response body with `Cache-Control: public, max-age=86400`, no ETag.

### 2.3 Auth (register / login / OTP)

Register: normalize contact → **consuming** `verifyOtp` → `existsByEmail/Phone` check → build `User` (BCrypt hash, `ui-avatars.com` default photo URL — no Cloudinary network call) → `save` → JWT. Login: normalize → `findByContact` → `passwordEncoder.matches` (same generic "Invalid credentials" either way — good) → JWT (`LoginResponse`, no user object). OTP send runs **inside `@Transactional`** and calls Brevo's HTTPS API **while holding the DB connection** (`service/OtpService.kt:56-75` → `service/EmailService.kt:67-74`). OTP verify has two modes: non-consuming `validateOtp` (the "verified ✓" tick) and consuming `verifyOtp` + `markAllAsUsed`.

### 2.4 People search (best endpoint, for contrast)

`GET /api/users` → 1 page `SELECT` (ILIKE on name/designation, active-only) + 1 `COUNT(*)`. `PeopleListItemResponse.fromEntity` touches scalars only — **2 statements total regardless of page size.** This is the pattern the feed should copy.

---

## 3. Problems and Bottlenecks

Severity: 🔴 CRITICAL · 🟠 HIGH · 🟡 MEDIUM · 🟢 LOW · ⚪ OPTIONAL — plus category tags.

### 🔴 CRITICAL

**C1. Feed N+1 — 102 statements per page of 20.** (Performance, Database)
Evidence in §2.1. At 500 users this is "slow feed"; at 5,000 it is an outage shape: ~100 round-trips per page, each `post_images` row drags BYTEA bytes that are thrown away, and Supabase pooler latency multiplies per-statement cost.

**C2. BYTEA images are eager-loaded on every read that touches users or posts.** (Performance, Database, Storage)
No `@Basic(fetch=LAZY)` anywhere (verified, 0 hits). `searchPeople`'s page `SELECT` fetches `profile_photo_data`/`cover_photo_data` for 20 users just to render photo *URLs*. Every feed author load fetches both photo blobs. Every `toPostResponse` fetches all image blobs to build URLs from IDs. You pay Postgres TOAST bandwidth + heap on reads that never display bytes.

**C3. Committed secrets.** (Security)
`application.properties:56` JWT fallback secret, `:65-67` real-looking Cloudinary key/secret, `:75` a personal Gmail as default sender, `:21-23` DB defaults. All in git history. Anyone with repo read access can forge JWTs on any deployment that forgot `JWT_SECRET`, and the Cloudinary secret must be assumed compromised (rotate it even though you barely use Cloudinary).

**C4. Stale avatars after photo change — cache bug.** (UX, Performance)
Stream URLs are stable (`/api/user/profile/photo/{userId}`) with `Cache-Control: public, max-age=86400` and no ETag/validator. After a user uploads a new photo, every client/CDN shows the old one for up to 24h. For a social app this reads as "upload is broken."

### 🟠 HIGH

**H1. OTP brute-force + OTP-send abuse: no rate limit, no attempt counting, no cooldown.** (Security)
6-digit numeric space (900k), `validateOtp` allows unlimited guesses (`service/OtpService.kt:108-112`, no counter), `sendOtp` (`service/OtpService.kt:57-75`) has no per-contact cooldown — an attacker can spam a victim's inbox (via your Brevo quota: 300/day free) and grind codes. `forgot-password` also confirms account existence ("No account found…" vs send path, `service/AuthService.kt:167-169`), enabling enumeration. (Note: `sendRegistrationOtp` at `service/AuthService.kt:150-152` likewise reveals whether a contact is registered.)

**H2. OTP codes logged at INFO.** (Security)
`service/OtpService.kt:147` logs `OTP for {} : {}` in plaintext. Logs are a secret store you didn't intend.

**H3. CORS `allowCredentials=true` + `allowedOriginPatterns=["*"]`.** (Security)
`config/SecurityConfig.kt:111-114`. Browsers reject `Access-Control-Allow-Origin: *` with credentials, so web clients break *and* the intent is over-permissive. (Mobile apps are unaffected — they don't do CORS.)

**H4. Concurrent double-like → 500.** (Concurrency)
`likePost` is check-then-insert with no lock (`service/NewsfeedService.kt:247-255`); two concurrent likes both pass `exists==false`, loser hits `uk_post_likes_post_user` (`entity/PostLike.kt:22`) → `DataIntegrityViolationException` → generic 500 (no graceful mapping on the like path). Same TOCTOU shape in double registration (saved by the 409 handler at `exception/GlobalExceptionHandler.kt:170-192`) and `shareCount` read-modify-write (lost increments — no `@Version` in any entity).

**H5. Brevo HTTPS call inside `@Transactional`.** (Performance, Architecture)
`OtpService.sendOtp` persists the OTP then calls `deliverOtp` → Brevo `RestClient.post()` while the DB transaction/connection is open. A Brevo slowdown becomes connection-pool exhaustion. (Skipped only when key/sender blank — `service/EmailService.kt:43-45`.)

**H6. Inactive users keep API access until token expiry.** (Security)
`security/JwtAuthenticationFilter.kt:71` loads by ID only, never checks `isActive`. Deactivation/ban takes up to 24h to bite.

### 🟡 MEDIUM

- **M1.** `ddl-auto=update` (`application.properties:37`) + `hibernate.format_sql` + `SQL=debug` logging — fine for testing, must not reach production (schema drift risk, log volume/cost).
- **M2.** Zero explicit indexes: `posts.created_at/author_id`, `post_images.post_id`, `comments.post_id/author_id/parent_id`, `otp_codes(contact,purpose,used,created_at)`, `users.updated_at`, `user_experiences/educations.user_id`. (Verified: `@Index` grep = 0 hits.) Tiny tables today → invisible; free to add; painful under load.
- **M3.** `createPost` runs `imageUrlsFor` twice (`service/NewsfeedService.kt:100` result discarded, recomputed in `toPostResponse`) — wastes one BYTEA-loading query per created post.
- **M4.** `sharePost` costs ~8 statements for a counter bump (double `getPostById` at `service/NewsfeedService.kt:290,295` + full 5-query `toPostResponse`).
- **M5.** `CurrentUser.getUserId()` throws `IllegalArgumentException/IllegalStateException` (`security/CurrentUser.kt:28-29`) → unmapped → 500 instead of 401. Same for `AccessDeniedException`, `MethodArgumentTypeMismatch`, `HttpRequestMethodNotSupported (405)`.
- **M6.** Comment tree is unbounded — `GET /{id}/comments` loads the entire thread into one response (`service/NewsfeedService.kt:342-359`). Fine at 500 users; a viral thread becomes a multi-MB payload.
- **M7.** Multipart cap inconsistency: `max-request-size=10MB` (`application.properties:48`) vs "5 images × 5 MB" allowance — a legit multi-image post can hit 413 before the count check runs.
- **M8.** No Hikari tuning (pool size/idle/timeout all defaults) against a Supabase pooler — fine locally, needs explicit small-pool settings on hosted deploys.
- **M9.** `server.error.include-message=always` (`application.properties:81`) — harmless with manual `ErrorResponse`, but non-`RestControllerAdvice` errors (e.g. filter-thrown) can leak Spring messages.

### 🟢 LOW

- Dead code: `/uploads/**` permitAll with **zero** serving handlers (`config/SecurityConfig.kt:64`); unused `UpdateProfileRequest` (live endpoint uses the Extended variant); dead `CloudinaryService.uploadImage` (+`CloudinaryConfig` bean still required at startup because `CloudinaryService` injects it); `PublicUserResponse.posts` hardcoded empty (`dto/response/PublicUserResponse.kt:26,48`); stale `entity/User.kt:79-80` Cloudinary comment.
- `POST`-only `/api/auth/**` matcher (a future non-POST auth path would 401 — latent, not live). Inert `@Valid` on `PUT /api/user/profile` and `POST /{id}/comments` (their DTOs carry no annotations). `sharePost` double `getPostById` (L1-cached, harmless).
- Missing `ETag`/`Last-Modified` on streams; no `Content-Disposition`. `BCrypt(10)` is fine — don't touch it.

### ⚪ OPTIONAL (do NOT do now)

Redis (nothing needs it yet — hottest read is the feed; fix the queries first; a 500-user DB answers 6 queries in single-digit ms), load balancer / 2nd instance / K8s / Kafka / microservices / CDN / Elasticsearch (ILIKE is fine to low-thousands), refresh-token rotation, cursor pagination.

---

## 4. Recommended Improvements

### R1. Fix the feed: 102 queries → ~6 (C1) 🔴

Keep the architecture; batch what `toPostResponse` does per row. Three changes:

**(a)** Stop loading BYTEA to build URLs — fetch IDs only:

```kotlin
// Current — NewsfeedService.kt:229-237 (loads image_data BYTEA per image, uses only id)
fun findByPostIdOrderBySortOrderAsc(postId: Long): List<PostImage>

// Recommended — PostImageRepository.kt (add; behavior identical, no migration)
@Query("SELECT pi.id FROM PostImage pi WHERE pi.post.id = :postId ORDER BY pi.sortOrder ASC")
fun findIdsByPostIdOrdered(@Param("postId") postId: Long): List<Long>
```

**(b)** JOIN FETCH the author once per page (kills query E for every row):

```kotlin
// Current — PostRepository.kt:22
fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<Post>

// Recommended — add alongside (no behavior change, no migration)
@Query(value = "SELECT p FROM Post p JOIN FETCH p.author ORDER BY p.createdAt DESC",
       countQuery = "SELECT COUNT(p) FROM Post p")
fun findFeedPage(pageable: Pageable): Page<Post>
```

**(c)** Batch the three per-post lookups into three page-level queries:

```kotlin
// Recommended — add to PostLikeRepository / CommentRepository (no migration)
@Query("SELECT l.post.id, COUNT(l) FROM PostLike l WHERE l.post.id IN :ids GROUP BY l.post.id")
fun countByPostIds(@Param("ids") ids: List<Long>): List<Array<Any>>

@Query("SELECT c.post.id, COUNT(c) FROM Comment c WHERE c.post.id IN :ids GROUP BY c.post.id")
fun countByPostIds(@Param("ids") ids: List<Long>): List<Array<Any>>

// liked ids for the viewer in one shot:
@Query("SELECT l.post.id FROM PostLike l WHERE l.post.id IN :ids AND l.user.id = :userId")
fun findLikedPostIds(@Param("ids") ids: List<Long>, @Param("userId") userId: Long): Set<Long>
```

Then `getFeed` = 1 page + 1 count + 1 ids-per-post + 1 like-counts + 1 comment-counts + 1 liked-ids ≈ **6 statements for any page size**. Same JSON, same endpoints — mobile app untouched. Expected benefit: latency (100+ round-trips → 6; on a pooler this is ~800 ms → ~60 ms P50), database load, memory (no BYTEA in heap).

### R2. Stop fetching bytes you don't display (C2) 🔴

- Short term (no migration): R1(a) above, plus accept `searchPeople`'s photo-blob fetch for now (20 users × photos is the smallest instance).
- Medium term (needs migration): move image bytes to object storage (Supabase Storage — account already exists), DB keeps only URLs; keep streaming endpoints as redirects during transition. Why: TOAST tables bloat backups, slow every `SELECT *`, and cap you at DB disk instead of cheap object storage. Do R1(a) now; plan the Storage move for Phase 3.

### R3. Cache-bust photo URLs (C4) 🟠

```kotlin
// Current — UserService.kt:182-186 (stable URL, cached 24h -> stale avatar)
.path("/api/user/$which/photo/{userId}").buildAndExpand(userId).toUriString()

// Recommended — version the URL with the row's updatedAt (no migration; new URL -> fresh fetch)
.path("/api/user/$which/photo/{userId}")
    .queryParam("v", user.updatedAt.toEpochSecond(ZoneOffset.UTC))
    .buildAndExpand(userId).toUriString()
```

Post images never change, so `imageUrlsFor` needs no versioning — only profile/cover. Behavior change: none except freshness. Reduces "my upload didn't work" complaints to zero.

### R4. Secrets + CORS + OTP-abuse hardening (C3, H1–H3, H6) + Brevo-after-commit (H5) 🔴/🟠

- Delete committed secrets from code; require env (fail fast if missing in prod profile); **rotate the Cloudinary secret and JWT secret** (both in history).
- `SecurityConfig.kt:111-114`: replace `allowedOriginPatterns("*") + allowCredentials(true)` with explicit origins for web clients (or `allowCredentials(false)` if only mobile consumes the API):

```kotlin
// Current — SecurityConfig.kt:111-114
allowedOriginPatterns = listOf("*")
// ...
allowCredentials = true

// Recommended (mobile-only API example)
allowedOriginPatterns = listOf()          // or explicit https origins for web
allowCredentials = false
```

- OTP: add `attempts INT DEFAULT 0` + `last_sent_at` to `otp_codes` (small migration); reject verify after ~5 wrong guesses; enforce ~60s resend cooldown in `sendOtp`; downgrade the OTP log to DEBUG without the code. Keep 6-digit + 10-min expiry — fine once guessing is bounded.
- `JwtAuthenticationFilter.kt:71`: add `&& user.isActive` so deactivation is immediate.
- Move Brevo out of the transaction — save OTP, then send after commit:

```kotlin
// Current — OtpService.kt:56-75 (HTTP inside tx)
// Recommended (same behavior on success; OTP row survives a Brevo failure)
@Transactional
fun sendOtp(contact: String, purpose: OtpPurpose) {
    // ... build + save otp ...
    TransactionSynchronizationManager.registerSynchronization(
        object : TransactionSynchronization {
            override fun afterCommit() = deliverOtp(contact, code)
        }
    )
}
```

### R5. Concurrency fixes (H4) 🟠

```kotlin
// Current — NewsfeedService.kt:247-255: check-then-insert -> concurrent 500
// Recommended — keep the check (fast path), catch the loser gracefully (no migration, same API)
try {
    if (!postLikeRepository.existsByPostIdAndUserId(postId, userId)) { /* ...save... */ }
} catch (e: DataIntegrityViolationException) {
    // lost the race: the other request already liked -> fall through as liked
}
```

```kotlin
// Current — NewsfeedService.kt:295-298: read-modify-write loses concurrent shares
// Recommended — atomic counter (no migration, same response)
@Modifying
@Query("UPDATE Post p SET p.shareCount = p.shareCount + 1 WHERE p.id = :id")
fun incrementShareCount(@Param("id") id: Long): Int
```

Double registration is already saved by the 409 handler — leave it.

### R6. Prod-readiness config (M1, M2, M5, M7–M9) 🟡 — before any public deploy

- `ddl-auto: update → validate` (+ Flyway for the two small migrations above),
- SQL-debug logging off; `maximum-pool-size≈10 / minimum-idle≈2` for the pooler,
- `max-request-size` raised to ~26 MB (or cap total upload MB in code),
- `server.error.include-message=never`,
- add `AccessDeniedException→403` / `MethodArgumentTypeMismatch→400` handlers,
- drop the dead `/uploads/**` rule and dead DTO,
- add missing indexes as `@Table(indexes=[...])` (free under `update` today): `posts(created_at)`, `posts(author_id)`, `post_images(post_id)`, `comments(post_id)`, `otp_codes(contact,purpose,used)`.

### What is deliberately NOT recommended

Cursor pagination (offset+COUNT is correct to tens of thousands of rows; revisit on slow-query evidence), Redis (measure after R1), DTO/interface projections via Spring magic (hand `fromEntity` is fine once queries are batched), microservices/K8s/Kafka/CDN/Elasticsearch, refresh tokens (24h expiry is a product decision, not a bug), raising BCrypt strength.

---

## 5. Implementation Roadmap

### Phase 1 — Do immediately (biggest benefit, all backward-compatible)

1. **R1 feed batching** (C1) — ~6 queries/page. Est: feed P50 800 ms → <100 ms on pooler; no mobile change, no migration.
2. **R3 photo URL versioning** (C4) — kills stale-avatar complaints; no migration.
3. **R5 race fixes + dead-code / `createPost` double-query cleanup** (H4, M3) — like-race 500 → gone; one fewer BYTEA query per post creation.

### Phase 2 — Before production (reliability + security)

4. **R4 secrets/CORS/OTP/Brevo-after-commit/isActive** — rotate secrets, explicit CORS origins, OTP attempts+cooldown, `ddl-auto=validate` + Flyway, prod logging/pool/error-message settings, M5 exception handlers, indexes (R6).
5. Add `spring-boot-starter-actuator` (health/info only) so hosts health-check honestly.

### Phase 3 — When traffic grows (5k+ users or feed complaints return)

6. Move image bytes to Supabase Storage (DB keeps URLs; streams become redirects) — the single biggest storage/cost lever.
7. Comment pagination (top-level pages + first-N replies + `replyCount`) instead of whole-tree responses.
8. Re-measure; only then consider Redis for hot feed pages / rate limiting, and cursor pagination if deep-offset scrolling appears in slow-query logs.

### Phase 4 — Only if necessary

Read replicas, CDN in front of Storage, refresh-token rotation, search service. None justified by anything in this codebase today.

---

## 6. Performance targets (post–Phase 1, localhost → Supabase pooler, P50/P95)

| API | P50 | P95 | Payload note |
|-----|-----|-----|--------------|
| `POST /auth/login` | <200 ms | <500 ms | BCrypt dominates — expected |
| `GET /feed` (20) | <100 ms | <300 ms | ~6 queries; JSON ≈ 20 × (URLs+counts), no bytes |
| `GET /feed/{id}` | <60 ms | <200 ms | ~6 queries |
| `GET /feed/{id}/comments` | <80 ms | <250 ms | grows with thread size — Phase 3 caps it |
| `POST /feed/{id}/like` | <80 ms | <250 ms | 3–5 queries |
| Image stream (≤5 MB) | <300 ms | <1 s | whole-bytes-in-heap until Storage move |
| `GET /users` search (20) | <100 ms | <300 ms | already 2 queries — keep |

---

## Appendix A — Current architecture

```
Mobile app (RN <Image>, no JWT on image URLs)
   │ JSON + multipart            Mobile <Image> (anonymous)
   ▼                                    │
┌──────────────────────────────────────────────────────┐
│ Controllers (JWT via CurrentUser, except image GETs)  │
│  AuthController  UserController  PeopleController     │
│  NewsfeedController  PageController                   │
├──────────────────────────────────────────────────────┤
│ Services (@Transactional; readOnly on reads)          │
│  AuthService  UserService  NewsfeedService  OtpService │
│  EmailService (Brevo HTTPS)  CloudinaryService (dead  │
│  upload path; live ui-avatars URL builder)            │
├──────────────────────────────────────────────────────┤
│ Repositories (Spring Data JPA, derived + 2 @Query)    │
├──────────────────────────────────────────────────────┤
│ PostgreSQL (Supabase pooler)                         │
│  users(+BYTEA photos) posts post_images(BYTEA)        │
│  post_likes comments otp_codes                       │
│  user_experiences user_educations                    │
│  relations: unidirectional ManyToOne child→parent     │
│  deletes: DB-level ON DELETE CASCADE only            │
└──────────────────────────────────────────────────────┘
Patterns: layered monolith · DTO+fromEntity · stateless JWT ·
          no roles · no events · no cache · no migrations tool
```

---

## Appendix B — Endpoint inventory (30 endpoints)

Auth (all `POST /api/auth/**`, permitAll): `register → 201 AuthResponse` · `login → 200 LoginResponse{token,tokenType,message}` · `otp/send → 200` · `otp/verify → 200` (non-consuming) · `forgot-password → 200` · `reset-password → 200`.
User (JWT): `GET/PUT /api/user/profile` · `POST /api/user/profile/photo` + public `GET .../photo/{userId}` · `POST /api/user/cover/photo` + public `GET ...` · `DELETE /api/user/experiences/{id}`, `DELETE .../educations/{id}` (404/403).
People (JWT): `GET /api/users?search&page&size` (2 queries always) · `GET /api/users/{id}` (public profile, 3 queries).
Feed (JWT except images): `POST /api/feed` (201, multipart content+≤5 images) · `GET /api/feed`, `GET /api/feed/mine` (paged, newest-first) · `GET /api/feed/{id}` · `DELETE /api/feed/{id}` (204, author-only) · `POST|DELETE /api/feed/{id}/like` (idempotent `LikeResponse`) · `POST /api/feed/{id}/share` (bumps counter) · `GET|POST /api/feed/{id}/comments` (nested tree; `parentId` = reply) · `DELETE /api/feed/comments/{cid}` (subtree delete) · public `GET /api/feed/images/{imageId}` (bytes + 24h cache).
Pages (public): `GET /api/terms`, `GET /api/privacy` (HTML from classpath; `/api/terms` is the deploy health-check).

---

## Appendix C — What was explicitly NOT recommended (and why)

| Tempting idea | Verdict | Why |
|---|---|---|
| Redis | NOT NEEDED | Hottest read (feed) is a query-shape problem; 6 indexed queries need no cache at 500–5,000 users |
| Cursor/keyset pagination | NOT NEEDED | Offset + COUNT is correct to tens of thousands of rows; no evidence of deep-offset pain |
| Microservices / K8s / Kafka | NOT NEEDED | Single deployable, ~75 files, one DB — distribution adds failure modes, removes none |
| CDN | OPTIONAL (Phase 3+) | Only matters after images leave Postgres for object storage |
| Elasticsearch | NOT NEEDED | ILIKE on name/designation is fine to low-thousands of users |
| Refresh-token rotation | OPTIONAL | 24h access token is a product call, not a defect |
| Eager `@Basic(fetch=LAZY)` LOBs | NOT RECOMMENDED | Needs bytecode enhancement; ID-only queries (R1a) solve the real cost without it |
| Raising BCrypt strength | NOT RECOMMENDED | 10 is the current sane default; login cost is expected |

---

## Appendix D — Items that could not be verified from code

- Production env-var values (DB URL, JWT secret, Brevo key on the host) and whether hosts still run old commits.
- Measured traffic/latency (targets in §6 are estimates from query counts, not observations).
- Live Supabase schema state — whether `ON DELETE CASCADE` constraints and defaults physically exist there, or the DB was created fresh by `ddl-auto`.
- Mobile-app caching behavior for image URLs (assumed standard HTTP-cache semantics for C4).
- Supabase pooler mode/settings (assumed transaction mode on 6543 per your connection string).
- Anything outside `src/main`, `build.gradle`, `Dockerfile`, `render.yaml` (tests, CI, infra-as-code were out of scope).

---

*Generated from a full source read of SMACian_Backend @ main (post-`e8b7a5b`). Scores: architecture consistency — good; urgent fixes — 4 critical, 6 high, all with backward-compatible paths above.*
