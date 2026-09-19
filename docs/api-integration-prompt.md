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
app can do full auth (register/login/OTP/password reset) and profile
management (view/edit/photo upload).

## API Base URL
- Production: `https://smacian-api.onrender.com`
- Keep it configurable (e.g. `BuildConfig.API_BASE_URL` per build type / debug
  override) -- never hardcode it in feature code, and never commit secrets.

## Auth model
- Stateless JWT. Successful `register` and `login` return the token directly.
- Every protected request sends: `Authorization: Bearer <token>`.
- Store the token securely (EncryptedSharedPreferences / DataStore), never in
  plain SharedPreferences or a global mutable singleton.

## Networking stack
- Use Retrofit + OkHttp + kotlinx-serialization (or the app's existing serialization
  library). Suspend functions / coroutines -- NO `enqueue`/callbacks.
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

### 1) POST /api/auth/otp/send  (public)
Request: `{ "contact": "<email or phone>" }`
Response 200: `{ "success": true, "message": "..." }`
- Used for BOTH registration and forgot-password. OTP expiry: 10 minutes.

### 2) POST /api/auth/otp/verify  (public)
Request: `{ "contact": "<email or phone>", "otpCode": "<6 digits>" }`
Response 200: `{ "success": true, "message": "..." }`
- This is a NON-consuming check. Final consumption happens at `register`.

### 3) POST /api/auth/register  (public)
Request:
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
Response **201**: `AuthResponse` (see below). Consumes the OTP.
Backend rules to mirror in client-side validation:
- `firstName`/`lastName`: 2-50 chars, letters and spaces only.
- `dateOfBirth`: `yyyy-MM-dd` string; user must be 13+.
- `gender`: one of `MALE | FEMALE | OTHER`.
- `contact`: max 255; email or phone.
- `password`: 8-100 chars, must contain uppercase + lowercase + digit +
  special character.
- `otpCode`: exactly 6 digits.
- `termsAccepted`: must be `true`.

### 4) POST /api/auth/login  (public)
Request: `{ "contact": "<email or phone>", "password": "..." }`
Response 200: `AuthResponse`.
- Wrong credentials -> 401 with the error envelope (message "Invalid email/phone
  or password"). Treat as a normal form error, not a session-expiry event.

### 5) POST /api/auth/forgot-password  (public)
Request: `{ "contact": "..." }`
Response 200: `MessageResponse`.

### 6) POST /api/auth/reset-password  (public)
Request: `{ "contact": "...", "otpCode": "482917", "newPassword": "...",
            "confirmPassword": "..." }`
Response 200: `MessageResponse`. (OTP is re-validated server-side.)

### 7) GET /api/user/profile  (Bearer)
Response 200: `UserResponse`.

### 8) PUT /api/user/profile  (Bearer)
Request: `{ "firstName": "...", "lastName": "...", "dateOfBirth": "yyyy-MM-dd",
            "gender": "MALE" }`
Response 200: `UserResponse`.

### 9) POST /api/user/profile/photo  (Bearer, multipart)
Send a file as part named **`file`** (that exact name). `Content-Type:
multipart/form-data`. Response 200: `UserResponse` with updated
`profilePhotoUrl`.

### 10) POST /api/user/cover/photo  (Bearer, multipart)
Same as above, part name **`file`** -> updates `coverPhotoUrl`.

## Shared DTOs

`AuthResponse`:
```json
{ "token": "...", "tokenType": "Bearer", "user": { ...UserResponse } }
```

`UserResponse`:
```json
{
  "id": 1,
  "firstName": "Ahmed",
  "lastName": "Khan",
  "email": "ahmed@example.com",
  "phone": null,
  "gender": "MALE",
  "dateOfBirth": "1998-01-12",
  "profilePhotoUrl": "https://...",
  "coverPhotoUrl": null,
  "isActive": true
}
```
(`email`/`phone`/`profilePhotoUrl`/`coverPhotoUrl`/`id` are nullable.)

`MessageResponse`: `{ "success": true, "message": "..." }`

## Engineering requirements (senior level)
- One `ApiService` interface per domain (auth, user) or a single clean one --
  keep naming consistent with existing code.
- Repository pattern behind the network layer, returning `Flow`/`StateFlow` for
  UI state, `suspend` functions for one-shot calls.
- Photo upload: stream from the file (not fully materialized into memory when
  avoidable), correct MIME type, and surface progress or at least a busy state.
- Validate fully on the FRONTEND before any network call. Run all client-side
  rules first (empty names, name charset, DOB 13+, gender set, exactly 6 digits
  for OTP, password strength, `confirmPassword == newPassword`,
  `termsAccepted == true`, non-empty contact, valid email/phone format). If
  anything is invalid, show the field errors immediately in the UI and DO NOT
  send the request -- the backend should only ever be reached with a locally
  valid payload.
- Reserve backend errors for things the client cannot know: wrong OTP value,
  OTP expired/used, contact already registered, wrong credentials, server or
  network failures. Parse the server error envelope (status/message/`fieldErrors`)
  only for these, and map `fieldErrors` 1:1 onto the same form fields you
  already validate client-side.
- Central session holder: after login/register store the token + user; after
  profile edits update the cached user; on 401 clear and navigate to login once.
- Mirror the server's field names exactly (Kotlin property names -> JSON keys:
  `otpCode`, `confirmPassword`, `newPassword`, `dateOfBirth`, `termsAccepted`).
- Document the base URL + usage for the team and keep swagger reference:
  `https://smacian-api.onrender.com/swagger-ui/index.html`.

List every file you create or change, implement it fully, and verify the
field names against the contracts above.
```