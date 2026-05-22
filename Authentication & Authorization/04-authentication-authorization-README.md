# Module 04 — Authentication & Authorization

> **The core questions:** *Who are you?* (Authentication) and *What can you do?* (Authorization)

Every application you will ever build needs to answer these two questions. Authentication and authorization are not just features — they are the security foundation of every backend system. This module covers the history, the concepts, the modern techniques, the tradeoffs, and the attack patterns you need to be aware of as a backend engineer.

---

## Table of Contents

- [Authentication vs Authorization — The Distinction](#authentication-vs-authorization--the-distinction)
- [A Brief History of Authentication](#a-brief-history-of-authentication)
- [Three Core Components](#three-core-components)
  - [Sessions](#1-sessions)
  - [JSON Web Tokens (JWT)](#2-json-web-tokens-jwt)
  - [Cookies](#3-cookies)
- [Types of Authentication](#types-of-authentication)
  - [Stateful Authentication](#1-stateful-authentication)
  - [Stateless Authentication](#2-stateless-authentication)
  - [API Key Authentication](#3-api-key-authentication)
  - [OAuth 2.0 & OpenID Connect](#4-oauth-20--openid-connect)
- [When to Use Which](#when-to-use-which)
- [Authorization & Role-Based Access Control](#authorization--role-based-access-control)
- [Security Pitfalls to Avoid](#security-pitfalls-to-avoid)
  - [Generic Error Messages](#generic-error-messages)
  - [Timing Attacks](#timing-attacks)
- [Demos in This Module](#demos-in-this-module)
- [Getting Started](#getting-started)

---

## Authentication vs Authorization — The Distinction

These two words are used interchangeably in casual conversation. They mean different things and they solve different problems.

| Concept | The Question It Answers | Example |
|---|---|---|
| **Authentication** | *Who are you?* | Verifying a username and password at login |
| **Authorization** | *What can you do?* | Checking if a logged-in user has admin permissions |

```
User logs in with email + password
        │
        ▼
  AUTHENTICATION  ← "Who are you?"
  Server verifies credentials
  Identity confirmed: Jane Doe, user ID 42
        │
        ▼
  AUTHORIZATION  ← "What can you do?"
  Server checks Jane's role: admin
  Admin can access /admin/dead-zone — access granted
```

Authentication always happens first. Authorization cannot happen without it — you cannot determine what someone is allowed to do until you know who they are.

---

## A Brief History of Authentication

Understanding where authentication came from helps you understand why today's solutions are designed the way they are.

| Era | Mechanism | Principle | Limitation |
|---|---|---|---|
| Pre-industrial | Village Elder vouching | Human trust | Doesn't scale beyond the community |
| Medieval | Wax seals on documents | *Something you possess* | Prone to forgery — first bypass attacks |
| Industrial | Telegraph pass phrases | *Something you know* | Static passwords — shared secrets |
| 1961 | MIT CTSS passwords (multi-user systems) | Stored passwords | Stored in plain text — first password leak |
| 1970s | Cryptographic research (Diffie-Hellman) | Asymmetric cryptography | Foundation of all modern auth protocols |
| 1990s | Multi-Factor Authentication (MFA) | Something you know + have + are | Biometrics introduced false positive/negative challenges |
| 2007 | OAuth 1.0 | Token delegation | Complex to implement, error-prone signatures |
| 2010 | OAuth 2.0 | Bearer tokens, multiple flows | Does not solve authentication — only authorization |
| 2014 | OpenID Connect | Identity layer on OAuth 2.0 | — |
| Today | Decentralised identity, Post-quantum cryptography | Blockchain, quantum-resistant algorithms | Still in early stages |

**The key thread across all of history:** every new authentication mechanism emerged because the previous one failed to scale, was forged, or was too complex to use safely.

---

## Three Core Components

Before diving into authentication types, you need to understand three building blocks that appear in almost every modern authentication flow.

### 1. Sessions

When HTTP was designed, it was **stateless** — the server remembered nothing between requests. That worked fine for reading static web pages. It broke down the moment applications needed to remember who you were across multiple requests (e.g. keeping items in a shopping cart, staying logged in while navigating pages).

Sessions solve this by giving the server short-term memory.

**How sessions work:**

```
Step 1 — Session Creation
  User logs in with email + password
          │
          ▼
  Server validates credentials
  Server creates a unique Session ID
  Server stores Session ID + user data in Redis/database
          │
          ▼
  { sessionId: "x9k2p...", userId: 42, role: "admin", cart: [...] }
  ← stored in persistent storage →

Step 2 — Session Delivery
  Server sends Session ID back to the browser
  in a cookie (HttpOnly — JavaScript cannot access it)

Step 3 — Subsequent Requests
  Browser automatically attaches the cookie to every request
          │
          ▼
  Server reads Session ID from cookie
  Server looks up Session ID in Redis
  Server retrieves user data
  Request is authorised
```

**Session storage evolution:**

| Phase | Storage | Why it changed |
|---|---|---|
| Early web | Files on the server | Simple but can't scale |
| Growing traffic | Database-backed sessions | Persistent across restarts |
| Distributed systems | Redis / Memcached (in-memory) | Much faster lookups, scales horizontally |

---

### 2. JSON Web Tokens (JWT)

By the mid-2000s, web applications had grown into globally distributed systems. Stateful sessions created three problems at scale:

- **Memory cost** — storing session data for millions of users is expensive
- **Replication** — synchronising session data across servers in different regions introduces latency
- **Consistency** — keeping session stores in sync across distributed systems is complex

JWTs solved this by making authentication **stateless** — the token itself carries all the information the server needs. No database lookup required.

**JWT structure:**

A JWT is three Base64-encoded strings separated by dots:

```
eyJhbGciOiJIUzI1NiJ9          ← Header
.eyJzdWIiOiI0MiIsInJvbGUiOiJhZG1pbiIsImlhdCI6MTcxNTc2MDAwMH0
                               ← Payload (claims)
.SflKxwRJSMeKKF2QT4fwpMeJf36P  ← Signature
```

**Header** — metadata about the token itself:
```json
{
  "alg": "HS256",
  "typ": "JWT"
}
```

**Payload** — the user data (called "claims"):
```json
{
  "sub": "42",
  "name": "Jane Doe",
  "role": "admin",
  "iat": 1715760000
}
```

> The payload is **Base64-encoded, not encrypted**. Anyone can decode it and read it. Never put passwords or sensitive data in a JWT payload.

**Signature** — proves the token has not been tampered with:
```
HMAC-SHA256(
  base64(header) + "." + base64(payload),
  SECRET_KEY
)
```

If anyone modifies the payload, the signature verification fails. The server rejects the token.

**Standard JWT fields:**

| Field | Name | Purpose |
|---|---|---|
| `sub` | Subject | The user's ID |
| `iat` | Issued At | When the token was created |
| `exp` | Expiry | When the token expires |
| `role` | Custom | The user's role (admin, user, etc.) |

**JWT advantages:**

- **Stateless** — no server-side storage required
- **Scalable** — any server with the secret key can verify any token
- **Portable** — can be sent in headers, cookies, or URL parameters

**JWT disadvantages:**

- **No revocation** — once issued, a JWT is valid until it expires. You cannot invalidate a single token without either changing the secret key (which logs out all users) or maintaining a blacklist (which re-introduces statefulness)
- **Token theft** — if an attacker obtains a valid JWT, they can impersonate that user until it expires

---

### 3. Cookies

A cookie is a small piece of data that a server stores in a user's browser. The browser automatically attaches that cookie to every subsequent request to that server.

**Why this matters for authentication:**

```
Server → sets cookie in browser → Session ID or JWT token stored here
Browser → sends cookie automatically → every request to that server includes it
Server → reads cookie → extracts token → validates user
```

Cookies automate the token transmission process. Without cookies, every client would have to manually attach the token to every request in code.

**Important cookie flags for security:**

| Flag | What it does | Why it matters |
|---|---|---|
| `HttpOnly` | JavaScript cannot read this cookie | Prevents XSS attacks from stealing the token |
| `Secure` | Cookie only sent over HTTPS | Prevents token interception over plain HTTP |
| `SameSite` | Controls cross-site sending | Prevents CSRF attacks |

---

## Types of Authentication

### 1. Stateful Authentication

The server stores session data. Every request is validated against a persistent store.

```
Client                          Server                      Redis
  │                               │                           │
  │── POST /login ───────────────>│                           │
  │   { email, password }         │                           │
  │                               │── validate credentials    │
  │                               │── create session ID       │
  │                               │── store session + user ──>│
  │<── 200 OK ────────────────────│                           │
  │   Set-Cookie: sessionId=x9k2  │                           │
  │                               │                           │
  │── GET /dashboard ────────────>│                           │
  │   Cookie: sessionId=x9k2      │── lookup session ID ─────>│
  │                               │<── user data ─────────────│
  │<── 200 OK ────────────────────│                           │
```

**Pros:** Centralized control, real-time revocation, easy to log out a user instantly

**Cons:** Requires persistent storage, synchronisation overhead in distributed systems

**Best for:** Web applications, SaaS platforms, any system where you need real-time session control

---

### 2. Stateless Authentication

The token carries all user information. No server-side storage lookup needed per request.

```
Client                          Server
  │                               │
  │── POST /login ───────────────>│
  │   { email, password }         │
  │                               │── validate credentials
  │                               │── sign JWT with secret key
  │<── 200 OK ────────────────────│
  │   { token: "eyJhbGci..." }    │
  │                               │
  │── GET /dashboard ────────────>│
  │   Authorization: Bearer eyJ.. │── verify signature with secret key
  │                               │── decode payload → userId, role
  │<── 200 OK ────────────────────│   (no database lookup needed)
```

**Pros:** No session store, horizontally scalable, any server with the secret can verify

**Cons:** Token revocation is complex, token theft window until expiry

**Best for:** APIs, distributed microservices, mobile apps, machine-to-machine communication

---

### 3. API Key Authentication

A pre-generated secret string used to authenticate programmatic access — without a login flow.

```
Developer                       Platform UI                    API Server
    │                               │                              │
    │── Click "Generate API Key" ──>│                              │
    │<── "sk-abc123xyz..." ─────────│                              │
    │                               │                              │
    │                               │                              │
    │── GET /api/data ──────────────────────────────────────────> │
    │   X-API-Key: sk-abc123xyz...                                 │── lookup key
    │<── 200 OK ───────────────────────────────────────────────── │   check permissions
```

**Why API keys exist:**

Stateful and stateless authentication require human interaction — someone types a username and password. API keys are designed for **machine-to-machine communication** where there is no human in the loop.

**Real-world example — OpenAI API:**
- You log into the OpenAI website (human interaction)
- You generate an API key
- Your server stores that key in an environment variable
- Your server uses that key to call OpenAI's models programmatically — no login form, no username/password exchange

**Pros:** Easy to generate, simple to use, ideal for programmatic access with scoped permissions

**Cons:** If leaked, key provides the access it was scoped for until revoked; no expiry by default unless configured

**Best for:** Third-party API integrations, machine-to-machine communication, developer tooling

---

### 4. OAuth 2.0 & OpenID Connect

**The problem they solve — the delegation problem:**

Before OAuth, if a travel app needed access to your Gmail to scan flight tickets, the only solution was to give the travel app your Gmail password. This was catastrophic:
- Full account access — no way to limit permissions
- No way to revoke access without changing your password everywhere

OAuth 2.0 (2010) replaced password sharing with **token delegation**:

```
You (Resource Owner)    Travel App (Client)    Google (Auth Server + Resource Server)
        │                      │                          │
        │<── "Allow access?" ──│                          │
        │── "Yes, read-only" ──>│                          │
        │                      │── exchange code ─────────>│
        │                      │<── access token ──────────│
        │                      │── GET /gmail/messages ───>│
        │                      │   Authorization: Bearer.. │
        │                      │<── your flight emails ────│
```

**Key OAuth 2.0 components:**

| Role | Who | Example |
|---|---|---|
| Resource Owner | The user who owns the data | You |
| Client | The app requesting access | Travel app |
| Resource Server | Where the data lives | Google's servers |
| Authorization Server | Issues tokens after user consent | Google's auth server |

**OAuth 2.0 flows by device type:**

| Flow | Used For |
|---|---|
| Authorization Code | Server-side web apps (most common, most secure) |
| Implicit | Browser-only apps (now discouraged — security risks) |
| Client Credentials | Machine-to-machine (no user involved) |
| Device Code | Smart TVs, CLI tools — limited input devices |

**What OAuth 2.0 does NOT solve:**

OAuth 2.0 handles **authorization** (what can the app access) but says nothing about **authentication** (who the user is). This gap led to OpenID Connect.

**OpenID Connect (OIDC) — adding identity to OAuth:**

Built on top of OAuth 2.0, OIDC introduces an **ID Token** (a JWT) that carries the user's identity:

```json
{
  "sub": "user-google-id-12345",
  "name": "Jane Doe",
  "email": "jane@gmail.com",
  "picture": "https://...",
  "iss": "https://accounts.google.com",
  "iat": 1715760000
}
```

This is what powers **"Sign in with Google"** and **"Sign in with GitHub"** across the web. The platform receiving the ID token knows exactly who the user is without managing their own authentication system.

**The full OIDC flow:**

```
User clicks "Sign in with Google" on a note-taking app
        │
        ▼
Browser redirects to Google's authorization server
        │
        ▼
User logs in to Google, grants permissions
        │
        ▼
Google sends Authorization Code + ID Token to note-taking app
        │
        ▼
Note-taking app exchanges code for Access Token
        │
        ▼
Note-taking app uses ID Token to identify the user (name, email, picture)
Note-taking app uses Access Token to access Google resources (e.g. Google Keep notes)
```

---

## When to Use Which

| Scenario | Recommended Approach |
|---|---|
| Web application with login/logout | **Stateful** (sessions + HttpOnly cookies) |
| REST API for mobile or third-party clients | **Stateless** (JWT in Authorization header) |
| Distributed microservices | **Stateless** (JWT — shared secret across services) |
| Programmatic API access / developer tooling | **API Keys** |
| "Sign in with Google/GitHub/Facebook" | **OAuth 2.0 + OpenID Connect** |
| Mixed (web app + APIs) | **Hybrid** — stateful for browsers, stateless for APIs |

> **Production advice:** Unless you are building authentication as the core product, use an established auth provider (Auth0, Clerk, Supabase Auth, Firebase Auth). Authentication is one of the most security-critical parts of any system. The goal of this module is to understand how it works — not necessarily to implement it from scratch in production.

---

## Authorization & Role-Based Access Control

Once you know who the user is, you need to determine what they can do. The most widely used technique is **Role-Based Access Control (RBAC)**.

**How RBAC works:**

Every user is assigned a role. Every role has a defined set of permissions. Permissions are scoped to specific resources and actions.

```
Roles and their permissions (example: note-taking platform)

  ROLE: user
    ✅ Create notes
    ✅ Read own notes
    ✅ Update own notes
    ✅ Delete own notes (moves to trash)
    ❌ Access dead zone (permanently deleted notes)
    ❌ Access other users' notes

  ROLE: admin
    ✅ All user permissions
    ✅ Access dead zone
    ✅ Permanently delete any note
    ✅ Access admin dashboard
    ✅ Manage all users
```

**The RBAC request flow:**

```
Incoming request: DELETE /admin/dead-zone/notes/42

  Step 1: Authentication
    Extract JWT from Authorization header
    Verify signature with secret key
    Decode payload → { userId: 99, role: "user" }

  Step 2: Authorization
    Required role for this endpoint: admin
    User's role: user
    ❌ Access denied

  Response: 403 Forbidden
    { "error": "You do not have permission to access this resource" }
```

**RBAC in a multi-tenant system (e.g. organisations):**

```
Organisation: Acme Corp
│
├── Admin (Jane)   → read, write, delete, manage members
├── Editor (Bob)   → read, write
└── Viewer (Alice) → read only
```

The granularity of RBAC scales with your application's complexity. Start simple (user / admin). Add roles as the application requires them.

---

## Security Pitfalls to Avoid

### Generic Error Messages

During authentication, helpful error messages become attack intelligence.

```
❌ DON'T — these messages leak information to attackers:

  "User not found"
  → Attacker learns: this email doesn't exist → move to the next one

  "Incorrect password"
  → Attacker learns: the email IS valid → now brute-force the password

  "Account locked due to too many attempts"
  → Attacker learns: this is a valid account under active attack
```

```
✅ DO — always use a generic message for all authentication failures:

  "Authentication failed"
  → Attacker learns: nothing
  → User knows: something was wrong with their credentials
```

This applies to every step of the authentication lifecycle — login, registration, password reset, token validation. Never confirm or deny whether a specific piece of identifying information (email, username) exists in your system.

---

### Timing Attacks

A subtle but real attack vector. The server's response time leaks information about which step of the authentication process failed.

**The problem:**

```
Scenario A — email not found in database:
  Step 1: Look up email → NOT FOUND → return immediately
  Response time: ~5ms

Scenario B — email found but password wrong:
  Step 1: Look up email → FOUND
  Step 2: Check if account is locked → NOT LOCKED
  Step 3: Hash the provided password → compare with stored hash
          (hashing is computationally expensive)
  Response time: ~200ms
```

An attacker can measure the difference in response time (5ms vs 200ms) to determine whether a given email address exists in your system — even if your error message says "Authentication failed" in both cases.

**The defence:**

```java
// Option 1: Always perform the password hash regardless of whether the user exists
// Use a dummy hash to run the comparison even when there's no real user
BCrypt.checkpw(providedPassword, DUMMY_HASH);  // same time cost as real comparison

// Option 2: Introduce a constant minimum response time
long startTime = System.currentTimeMillis();
boolean authenticated = authenticate(email, password);
long elapsed = System.currentTimeMillis() - startTime;
long minResponseMs = 300;
if (elapsed < minResponseMs) {
    Thread.sleep(minResponseMs - elapsed);  // equalise response time
}
```

Both approaches ensure that a correct email and a wrong email produce responses in the same time window, eliminating the timing signal attackers rely on.

---

## Demos in This Module

| # | Demo | Concept |
|---|---|---|
| 01 | [Session-Based Auth](./01-session-auth/) | Stateful login, session creation, Redis storage, logout |
| 02 | [JWT Auth](./02-jwt-auth/) | Stateless login, token signing, verification, expiry |
| 03 | [Cookies](./03-cookies/) | HttpOnly cookies, Secure flag, SameSite — and why they matter |
| 04 | [API Keys](./04-api-keys/) | Generating, storing, and validating API keys |
| 05 | [OAuth 2.0 Flow](./05-oauth2/) | Authorization Code flow with a mock auth server |
| 06 | [RBAC](./06-rbac/) | Role assignment, permission checks, 403 responses |
| 07 | [Hybrid Auth](./07-hybrid-auth/) | Stateful for web + stateless for API, running side by side |
| 08 | [Security Pitfalls](./08-security-pitfalls/) | Generic errors and timing attack simulation |

---

## Getting Started

### Prerequisites

- Node.js v18+ (or Java 17+ for Spring Boot demos)
- Redis running locally (`docker run -p 6379:6379 redis` if you have Docker)
- curl or Postman

### Run the session auth demo

```bash
git clone [repository-url]
cd [repository-name]/04-authentication

cd 01-session-auth
npm install
node server.js

# Register a user
curl -X POST http://localhost:3000/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "jane@example.com", "password": "SecurePass123"}'

# Login — observe the Set-Cookie header in the response
curl -v -X POST http://localhost:3000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "jane@example.com", "password": "SecurePass123"}'

# Access a protected route using the session cookie
curl http://localhost:3000/dashboard \
  -H "Cookie: sessionId=<value-from-login-response>"
```

Each demo's `README.md` includes the curl commands, what to observe in the network tab, and a **"Break It On Purpose"** section — tamper with the JWT signature, send an expired token, or try to access an admin route as a regular user.

---

> **Next module:** [Module 05 — Databases](../05-databases/) — your authentication system stores users somewhere. Next we look at how data gets persisted, queried, and managed reliably.
