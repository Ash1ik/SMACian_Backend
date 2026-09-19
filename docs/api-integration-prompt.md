# API Integration Prompt (Mobile App)

Copy-paste the block below into your mobile app's AI chat.

```markdown
You are a senior Android/Kotlin engineer. You already have full context of this
app's codebase (architecture, package structure, DI setup, ViewModel/state
patterns, naming conventions, networking stack if one exists). Follow those
conventions EXACTLY -- do NOT invent a new architecture or ask clarifying
questions about how this project is organized. Extend what's already there.

## Task
Implement the complete REST API integration for the SMACian Backend so the
app can do full auth (register/login/OTP/password reset), profile management
(extended profile with work experience, education, social links, photos) and
social discovery (people search, viewing other users' public profiles).

## API Base URL
- Production: `https://smacian-api.onrender.com`
- Keep it configurable (e.g. `BuildConfig.API_BASE_URL` per build type / debug
  override) -- never hardcode it in feature code, and never commit secrets.
- Interactive API reference (for field names): 
  `https://smacian-api.onrender.com/swagger-ui/index.html`

## Auth model
- Stateless JWT. Successful `register` and `login` return the token directly.
- Every request under `/api/user` and `/api/users` sends:
  `Authorization: Bearer <token>`. Auth endpoints are public.
- Store the token securely (EncryptedSharedPreferences / DataStore), never in
  plain SharedPreferences or a global mutable singleton.

## Networking stack
- Use Retrofit + OkHttp + kotlinx-serialization (or the app's existing
  serialization library). Suspend functions / coroutines -- NO `enqueue`/callbacks.
- OkHttp `Interceptor` adds the Authorization header automatically. The header
  interceptor must SKIP protected endpoints if the token is missing (only auth
  endpoints are public) so known-public calls don't send a token.
- Handle 401 centrally: on an expired/invalid token clear the session and emit
  the "logged out" state (or trigger re-login) -- don't scatter this logic in
  every repository.

## Unified result + error handling
- Non-2xx responses ALWAYS use this exact JSON shape from the backend
  (single object, NOT wrapped):

```json
{
  "success": false,
  "timestamp": "2026-09-18T10:00:00",
  "status": 400,
  "error": "Validation Failed",
  "message": "Please fix the following errors",
  "path": "/api/auth/register",
  "fieldErrors": [
    { "field": "password", "message": "Password must contain a digit" }
  ]
}
```

  `fieldErrors` is `null` (not a list) for non-validation errors. Build a
  sealed `ApiResult`/`Result` wrapper that carries either the success DTO or an
  error domain object exposing `status`, `message`, and mapped `field`->error.
  Map `fieldErrors` to form field error texts in the UI layer; fall back to
  `message` for form-level (CRUD) errors.

## Endpoints to implement

### Auth (public)
1) `POST /api/auth/otp/send`  `{ "contact": "<email or phone>" }` -> 200
   `{ "success": true, "message": "..." }`. Used for BOTH registration and
   forgot-password. OTP expiry: 10 minutes.
2) `POST /api/auth/otp/verify`  `{ "contact": "...", "otpCode": "<6 digits>" }`
   -> 200. Non-consuming check; final consumption happens at `register`.
3) `POST /api/auth/register` -> **201** `AuthResponse`. Consumes the OTP.
   ```json
   {
     "firstName": "Ahmed",
     "lastName": "Khan",
     "dateOfBirth": "1998-01-12",
     "gender": "MALE",
     "contact": "ahmed@example.com",
     "password": "MyP@ss123",
     "otpCode": "482917",
     "termsAccepted": true
   }
   ```
   Client-side rules to mirror: firstName/lastName 2-50 letters+spaces;
   dateOfBirth `yyyy-MM-dd` and 13+ years old; gender `MALE|FEMALE|OTHER`;
   contact max 255 (email or phone); password 8-100 with upper+lower+digit+
   special char; otpCode exactly 6 digits; termsAccepted must be true.
4) `POST /api/auth/login`  `{ "contact": "...", "password": "..." }` -> 200
   `AuthResponse`. Wrong credentials -> 401 (treat as a normal form error,
   NOT a session-expiry event).
5) `POST /api/auth/forgot-password`  `{ "contact": "..." }` -> 200.
6) `POST /api/auth/reset-password`
   `{ "contact": "...", "otpCode": "482917", "newPassword": "...",
     "confirmPassword": "..." }` -> 200. (OTP re-validated server-side.)

### My profile (Bearer: `/api/user`)
7) `GET /api/user/profile` -> 200 `UserResponse`.
8) `PUT /api/user/profile` -> 200 `UserResponse`. **FULL update**: every
   scalar field NOT sent is cleared to null, so ALWAYS send the complete
   profile object, never sparse diffs. Experience/education with an `id` are
   UPDATED (must be your own, else 403), without an `id` they are CREATED;
   entries you don't mention are left untouched (never deleted).
   ```json
   {
     "firstName": "Ahmed",
     "lastName": "Khan",
     "dateOfBirth": "1998-01-12",
     "gender": "MALE",
     "designation": "Senior Software Engineer",
     "bio": "Building things people love.",
     "email": "ahmed@example.com",
     "phone": "01712345678",
     "location": "Dhaka, Bangladesh",
     "bloodGroup": "A+",
     "relationshipStatus": "Single",
     "socialLinks": ["https://linkedin.com/in/ahmed"],
     "experiences": [
       {
         "id": null,
         "organization": "TechCorp",
         "designation": "Engineer",
         "startDate": "Jan 2022",
         "endDate": "Present",
         "description": "Built the payments API"
       }
     ],
     "educations": [
       { "id": null, "institution": "BUET", "fieldOfStudy": "CSE",
         "startDate": "Sep 2018", "endDate": "Jun 2021" }
     ]
   }
   ```
   Server-side validation to mirror (UI-keyed `fieldErrors`):
   - firstName/lastName: required, 2-50 letters+spaces.
   - dateOfBirth: required `yyyy-MM-dd`, after 1950, not future, age 13+.
   - gender: required `MALE|FEMALE|OTHER`; designation<=100; bio<=500;
     location<=255; email/phone must be unique (server checks) and valid
     format when provided (client: validate format; if "already exists" comes
     back, show it on that field).
   - bloodGroup: one of `A+ A- B+ B- AB+ AB- O+ O-`; relationshipStatus:
     `Single | In a relationship | Engaged | Married`.
   - socialLinks: max 10, each a valid `http(s)://` URL.
   - experience: organization/designation required 2-100; startDate/endDate
     `"MMM yyyy"` (e.g. `"Jan 2025"`, capital month, English); endDate may be
     the literal `"Present"`; endDate cannot be before startDate.
   - education: institution/fieldOfStudy required 2-100; same date rules.
9) `POST /api/user/profile/photo` (Bearer, multipart) -- part name **`file`**
   (exact). -> 200 `UserResponse` with updated `profilePhotoUrl`.
10) `POST /api/user/cover/photo` (Bearer, multipart) -- part name **`file`**
    -> 200 `UserResponse` with updated `coverPhotoUrl`.
11) `DELETE /api/user/experiences/{id}` -> 204 No Content (own entry only;
    403 if the entry belongs to another user; 404 if it doesn't exist).
12) `DELETE /api/user/educations/{id}` -> same contract as #11.

### People / discovery (Bearer: `/api/users`)
13) `GET /api/users?search=ahmed&page=0&size=20` -> 200 `PagedResponse`.
    - search is optional (empty = first page of everyone), case-insensitive
      match on full name OR designation. Response:
    ```json
    {
      "content": [
        { "id": 1, "fullName": "Ahmed Khan",
          "profilePhotoUrl": "https://...", "designation": "Engineer",
          "bloodGroup": "A+" }
      ],
      "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
    }
    ```
    - Implement debounced search-as-you-type; `page`/`size` for "load more"
      (append content, stop when page >= totalPages - 1).
14) `GET /api/users/{id}` -> 200 `PublicUserResponse`. Email and phone are
    DELIBERATELY OMITTED. 404 if not found or inactive.
    ```json
    {
      "id": 1, "fullName": "Ahmed Khan", "profilePhotoUrl": "...",
      "coverPhotoUrl": null, "designation": "...", "bio": "...",
      "location": "...", "bloodGroup": "A+", "gender": "MALE",
      "relationshipStatus": "Single",
      "socialLinks": ["https://..."],
      "experience": [ { ...UserExperienceResponse } ],
      "education": [ { ...UserEducationResponse } ],
      "posts": []
    }
    ```

## Shared DTOs

`AuthResponse`: `{ "token": "...", "tokenType": "Bearer", "user": { ...UserResponse } }`

`UserResponse` (self profile -- your own, includes email/phone):
```json
{
  "id": 1, "firstName": "Ahmed", "lastName": "Khan",
  "email": "ahmed@example.com", "phone": "01712345678",
  "gender": "MALE", "dateOfBirth": "1998-01-12",
  "profilePhotoUrl": "https://...", "coverPhotoUrl": null, "isActive": true,
  "designation": "Senior Software Engineer", "bio": "...", "location": "...",
  "bloodGroup": "A+", "relationshipStatus": "Single",
  "socialLinks": ["https://linkedin.com/in/ahmed"],
  "experiences": [
    { "id": 1, "organization": "TechCorp", "designation": "Engineer",
      "startDate": "Jan 2022", "endDate": "Present", "description": "..." }
  ],
  "educations": [
    { "id": 1, "institution": "BUET", "fieldOfStudy": "CSE",
      "startDate": "Sep 2018", "endDate": "Jun 2021" }
  ],
  "updatedAt": "2026-09-18T10:00:00"
}
```
Nullable: `id`, `email`, `phone`, `profilePhotoUrl`, `coverPhotoUrl`,
`designation`, `bio`, `location`, `bloodGroup`, `relationshipStatus`,
`updatedAt`. `socialLinks`/`experiences`/`educations` are never null (empty
array instead). Enum fields serialize as labels (`bloodGroup: "A+"`,
`relationshipStatus: "Single"`).

`PagedResponse<T>`: `{ "content": [...], "page": 0, "size": 20,
"totalElements": 42, "totalPages": 3 }`

`MessageResponse`: `{ "success": true, "message": "..." }`

## Engineering requirements (senior level)
- One `ApiService` interface per domain (auth, user, people) or a single clean
  one -- keep naming consistent with existing code.
- Repository pattern behind the network layer, returning `Flow`/`StateFlow`
  for UI state, `suspend` functions for one-shot calls.
- Photo upload: stream from the file (not fully materialized into memory when
  avoidable), correct MIME type, and surface progress or at least a busy state.
- Profile edit form: reuse the SAME local DTO as the server's
  `UpdateProfileRequestExtended` shape; initialize it fully from the cached
  `UserResponse` so re-submitting never accidentally clears a field the user
  didn't touch (the PUT is a FULL update).
- Experience/Education editor: prepopulate with the user's existing entries
  (they carry `id`). New rows have `id = null`. On save, upsert is automatic.
  Deletes happen via the dedicated DELETE endpoints (or "delete" chip in the
  row's edit screen).
- People list: pull-to-refresh reloads page 0; infinite scroll appends pages;
  tapping a row opens the public profile screen.
- Validate fully on the FRONTEND before any network call. Run all client-side
  rules first (names, DOB 13+, gender set, password strength, exact-6 OTP,
  email/phone format, bloodGroup/relationship value, http(s)+max-10 social
  links, `"MMM yyyy"` dates, end >= start unless "Present", required
  org/institution/etc.). If anything is invalid, show the field errors
  immediately in the UI and DO NOT send the request -- the backend should
  only ever be reached with a locally valid payload.
- Reserve backend errors for things the client cannot know: wrong OTP value,
  OTP expired/used, contact already registered, wrong credentials, unique
  email/phone conflict on profile save, forbidden ownership (403), not found
  (404), network failures. Parse the server error envelope
  (status/message/`fieldErrors`) only for these and map `fieldErrors` 1:1 onto
  the same form fields you already validate client-side.
- Central session holder: after login/register store the token + user; after
  profile edits update the cached user; on 401 clear and navigate to login once.
- Mirror the server's field names exactly (Kotlin property names -> JSON keys:
  `otpCode`, `confirmPassword`, `newPassword`, `dateOfBirth`, `termsAccepted`,
  `bloodGroup`, `relationshipStatus`, `socialLinks`, `experiences`,
  `educations`, `organization`, `fieldOfStudy`, `startDate`, `endDate`).
- Document the base URL + usage for the team and keep swagger reference:
  `https://smacian-api.onrender.com/swagger-ui/index.html`.

List every file you create or change, implement it fully, and verify the
field names against the contracts above.
```