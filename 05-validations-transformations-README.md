# Module 05 — Validations & Transformations

> **The core question:** Before your business logic runs, how do you guarantee the data your server received is actually usable?

Validation and transformation are not optional polish you add at the end. They are the **first line of defence** between the unpredictable outside world and the deterministic logic your service layer expects. Get this wrong and your server either breaks under unexpected input or silently stores corrupt data. Get it right and every layer below the entry point can trust the data it receives.

---

## Table of Contents

- [Where in the Stack This Happens](#where-in-the-stack-this-happens)
- [Why Validate at the Entry Point?](#why-validate-at-the-entry-point)
- [Types of Validation](#types-of-validation)
  - [1. Type Validation](#1-type-validation)
  - [2. Syntactic Validation](#2-syntactic-validation)
  - [3. Semantic Validation](#3-semantic-validation)
  - [4. Complex / Cross-Field Validation](#4-complex--cross-field-validation)
- [Transformation](#transformation)
  - [Why Transformation Exists](#why-transformation-exists)
  - [Common Transformation Operations](#common-transformation-operations)
- [The Validation + Transformation Pipeline](#the-validation--transformation-pipeline)
- [Frontend Validation vs Backend Validation](#frontend-validation-vs-backend-validation)
- [Demos in This Module](#demos-in-this-module)
- [Getting Started](#getting-started)

---

## Where in the Stack This Happens

A typical backend application has three layers. Each has a distinct responsibility:

```
┌─────────────────────────────────────────────────────────────┐
│  Controller Layer                                           │
│  Handles HTTP — receives requests, returns responses        │
│  ← VALIDATION & TRANSFORMATION HAPPEN HERE, AT THE TOP ─── │
└──────────────────────────┬──────────────────────────────────┘
                           │ (only clean, validated data passes through)
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  Service Layer                                              │
│  Executes business logic — sends emails, calls webhooks,    │
│  orchestrates operations                                    │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  Repository Layer                                           │
│  Database operations — queries, inserts, updates, deletes   │
└─────────────────────────────────────────────────────────────┘
```

**The precise moment validation runs:**

```
Incoming HTTP Request
        │
        ▼
  Route Matching Algorithm
        │
        ▼
  Route matched → Controller method identified
        │
        ▼  ← VALIDATION & TRANSFORMATION RUN HERE
  Validation Pipeline
        │
        ├── FAIL → return 400 Bad Request immediately
        │          (service layer is never called)
        │
        └── PASS → Controller calls Service Layer
                         │
                         ▼
                   Service calls Repository
                         │
                         ▼
                   Response returned to client
```

Before any significant logic runs — before a single database query fires, before any email is sent, before any external API is called — the incoming data must pass through the validation and transformation pipeline.

---

## Why Validate at the Entry Point?

Consider what happens when you skip validation.

**Without validation:**

```
Client sends:  POST /api/books
               { "name": 0 }   ← number, not a string

Server flow:
  1. Controller receives data
  2. Controller calls service
  3. Service calls repository
  4. Repository executes: INSERT INTO books (name) VALUES (0)
  5. Database rejects: type constraint violation (column expects TEXT)
  6. Repository throws an exception
  7. Server returns: 500 Internal Server Error
```

The client receives a `500 Internal Server Error` for what was a simple client mistake — sending the wrong data type. From the client's perspective, the server is broken. From a security perspective, the stack trace might leak implementation details.

**With validation:**

```
Client sends:  POST /api/books
               { "name": 0 }   ← number, not a string

Server flow:
  1. Validation pipeline runs
  2. name field fails type check (expected string, received number)
  3. Returns immediately: 400 Bad Request
     { "errors": [{ "field": "name", "message": "Expected string, received number" }] }

  Service layer: never called
  Database: never touched
```

The client gets a clear, actionable error. The database never sees invalid data. The service layer never runs in an invalid state. This is the purpose of entry-point validation.

---

## Types of Validation

### 1. Type Validation

The most fundamental check. Ensures each field contains the data type the API expects.

```
Expected: string    Received: 42         → FAIL
Expected: number    Received: "hello"    → FAIL
Expected: boolean   Received: "true"     → FAIL (string, not boolean)
Expected: array     Received: {}         → FAIL (object, not array)
Expected: string[]  Received: [1, 2, 3]  → FAIL (array of numbers, not strings)
```

**Example validation schema:**

```json
{
  "stringField":  { "type": "string"  },
  "numberField":  { "type": "number"  },
  "booleanField": { "type": "boolean" },
  "arrayField":   { "type": "array", "items": { "type": "string" } }
}
```

**What a failed type validation response looks like:**

```json
{
  "errors": [
    { "field": "numberField",  "message": "Expected number, received string" },
    { "field": "booleanField", "message": "Expected boolean, received string" },
    { "field": "arrayField",   "message": "Expected array, received string"  }
  ]
}
```

---

### 2. Syntactic Validation

Confirms the structure or format of a value matches a well-defined pattern — regardless of whether the value makes logical sense.

Common examples:

| Field | Pattern it must match | Failure example |
|---|---|---|
| Email | `local@domain.tld` | `"notanemail"` |
| Phone number | Country code + digit pattern | `"12345"` (no country code) |
| Date | `YYYY-MM-DD` or other specified format | `"5/11/25"` |
| URL | `https://...` | `"google.com"` (missing scheme) |
| UUID | `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx` | `"abc123"` |
| Postal code | Country-specific pattern | `"HELLO"` |

**Email example:**

```
Valid:   jane@example.com     ✅
Valid:   j.doe+tag@company.co ✅
Invalid: janeatexample.com    ❌ (missing @)
Invalid: jane@               ❌ (missing domain)
Invalid: @example.com        ❌ (missing local part)
```

> **Key distinction from semantic validation:** a syntactically valid email like `test@test.com` might not exist or be deliverable — syntactic validation only checks the *format*, not the *meaning*.

---

### 3. Semantic Validation

Confirms the *meaning* or *logical sense* of a value, not just its format. These rules are business-domain specific — they encode what makes sense in the real world.

**Examples:**

```
Date of birth must not be in the future
  Provided: 2030-01-15  → FAIL: "Date of birth cannot be in the future"

Age must be within a human-plausible range (1–120)
  Provided: 430         → FAIL: "Age must be less than or equal to 120"
  Provided: -5          → FAIL: "Age must be a positive number"

Start date must be before end date
  start: 2026-06-01, end: 2026-05-01  → FAIL: "Start date must be before end date"

Discount percentage must be between 0 and 100
  Provided: 150         → FAIL: "Discount cannot exceed 100%"
```

Semantic validation protects data integrity at the business-logic level. A date can be syntactically valid (correct format) and still be semantically invalid (in the future).

---

### 4. Complex / Cross-Field Validation

Validation that involves the relationship between multiple fields rather than a single field in isolation.

**Password confirmation example:**

```json
{
  "password":             "MySecurePass1!",
  "passwordConfirmation": "MySecurePass1!"
}
```

Rule: `passwordConfirmation` must equal `password`.

```
"password": "abc12345",  "passwordConfirmation": "abc99999"
→ FAIL: "Password confirmation must match password"
```

**Conditional field example:**

```json
{
  "married": true,
  "partnerName": ""     ← required ONLY when married is true
}
```

Rules:
- `partnerName` is optional when `married` is `false`
- `partnerName` is required when `married` is `true`

```
{ "married": false }
→ PASS (partnerName not required)

{ "married": true }
→ FAIL: "Partner name is required when married is true"

{ "married": true, "partnerName": "John Doe" }
→ PASS
```

These rules cannot be expressed as independent field checks — they require knowledge of the full payload.

---

## Transformation

### Why Transformation Exists

Not all incoming data arrives in the format your service layer needs. Sometimes the mismatch is not the client's fault — it is a constraint of the medium. **Transformation converts data into the shape the server requires**, either before or after validation.

**The most common example — query parameters:**

Every value in a query string is a string by default. The client cannot send a typed number; the transport layer does not support it.

```
Client sends:  GET /api/bookmarks?page=2&limit=20

Server receives:
  page:  "2"   ← string, not number
  limit: "20"  ← string, not number
```

If your validation schema says `page` must be a `number`, this fails — even though the client sent a perfectly reasonable value. The fix is to **transform first, then validate**:

```
"2"  → parseInt("2")  → 2   → validate: is number? ✅ is > 0? ✅ is < 500? ✅
"20" → parseInt("20") → 20  → validate: is number? ✅ is > 0? ✅ is < 10000? ✅
```

### Common Transformation Operations

| Operation | Input | Output | Why |
|---|---|---|---|
| String → Number | `"42"` | `42` | Query params are always strings |
| String → Boolean | `"true"` | `true` | Same reason |
| Email → lowercase | `"Jane@GMAIL.COM"` | `"jane@gmail.com"` | Normalise for storage/comparison |
| Phone → E.164 format | `"9876543210"` | `"+919876543210"` | Consistent format in database |
| Date → ISO 8601 | `"5/11/25"` | `"2025-11-05T00:00:00Z"` | Consistent timestamp format |
| Trim whitespace | `"  Jane  "` | `"Jane"` | Prevent padded string mismatches |
| Strip HTML tags | `"<b>Hello</b>"` | `"Hello"` | Prevent XSS in stored content |

---

## The Validation + Transformation Pipeline

Validation and transformation are combined into a single pipeline so all input-data logic lives in one place. No hunting across layers to understand what shape the data is in.

```
Incoming data (JSON body / query params / path params / headers)
        │
        ▼
┌─────────────────────────────────────────────────┐
│  Validation + Transformation Pipeline           │
│                                                 │
│  Step 1: Presence check                         │
│    Is the required field present at all?        │
│    → FAIL: "name is required"                   │
│                                                 │
│  Step 2: Transformation (if needed)             │
│    Convert types: "2" → 2                       │
│    Normalise: "JANE@GMAIL.COM" → "jane@gmail.com"│
│                                                 │
│  Step 3: Type validation                        │
│    Is the value the expected type?              │
│    → FAIL: "Expected number, received string"   │
│                                                 │
│  Step 4: Syntactic validation                   │
│    Does the value match the expected format?    │
│    → FAIL: "Invalid email format"               │
│                                                 │
│  Step 5: Semantic validation                    │
│    Does the value make logical sense?           │
│    → FAIL: "Date of birth cannot be in the future" │
│                                                 │
│  Step 6: Cross-field validation                 │
│    Do related fields agree with each other?     │
│    → FAIL: "Passwords do not match"             │
└────────────────┬────────────────────────────────┘
                 │
        ┌────────┴────────┐
      FAIL              PASS
        │                 │
        ▼                 ▼
  400 Bad Request    Controller calls
  with all errors    Service Layer
  in one response    (clean data guaranteed)
```

> **Return all errors at once.** Do not fail on the first error and force the client to fix issues one at a time. Collect all validation failures and return them in a single `400` response. This is a basic UX courtesy for any API consumer.

---

## Frontend Validation vs Backend Validation

This is one of the most common misunderstandings in web development. Both exist. Neither replaces the other.

| | Frontend Validation | Backend Validation |
|---|---|---|
| **Purpose** | User experience — immediate feedback | Security and data integrity |
| **When it runs** | Before the API call is made | After the API call arrives |
| **Can be bypassed?** | Yes — always | No — it is the last line of defence |
| **Who it protects** | The user (saves a round trip) | The server and database |
| **Required?** | Recommended | Mandatory |

**The critical rule:**

> Backend validation must exist regardless of what the frontend does. The server has no guarantee that any client calling it has any validation at all.

Your API can be called by:
- A React app with full form validation
- A mobile app with no validation
- A `curl` command from a terminal
- An automated script
- A malicious actor trying to inject bad data

From the server's perspective, all of these are just HTTP requests. The server cannot know which client sent the request. Backend validation must assume the worst every time.

```
Frontend validation:  for the user
Backend validation:   for the server

One is a courtesy.
The other is a requirement.
```

---

## Demos in This Module

| # | Demo | Concept |
|---|---|---|
| 01 | [Type Validation](./01-type-validation/) | Enforcing string, number, boolean, array types on request fields |
| 02 | [Syntactic Validation](./02-syntactic-validation/) | Email format, phone pattern, date structure |
| 03 | [Semantic Validation](./03-semantic-validation/) | Future date rejection, age range, logical consistency |
| 04 | [Complex Validation](./04-complex-validation/) | Password confirmation match, conditional required fields |
| 05 | [Transformation](./05-transformation/) | Query param casting, email normalisation, phone formatting |
| 06 | [Full Pipeline](./06-full-pipeline/) | All types combined — one endpoint, full validate + transform flow |
| 07 | [Frontend + Backend](./07-frontend-and-backend/) | Form with client-side validation calling a server with server-side validation |

---

## Getting Started

### Prerequisites

- Node.js v18+ (or Java 17+ for Spring Boot demos)
- curl, Postman, or Insomnia

### Run the type validation demo

```bash
git clone [repository-url]
cd [repository-name]/05-validations

cd 01-type-validation
node server.js

# Send correct types — should succeed
curl -X POST http://localhost:3000/api/validate/types \
  -H "Content-Type: application/json" \
  -d '{
    "stringField": "hello",
    "numberField": 42,
    "booleanField": false,
    "arrayField": ["a", "b", "c"]
  }'

# Send wrong types — observe the validation errors
curl -X POST http://localhost:3000/api/validate/types \
  -H "Content-Type: application/json" \
  -d '{
    "stringField": "hello",
    "numberField": "not-a-number",
    "booleanField": "true",
    "arrayField": [1, 2, 3]
  }'

# Send an empty body — observe ALL required field errors at once
curl -X POST http://localhost:3000/api/validate/types \
  -H "Content-Type: application/json" \
  -d '{}'
```

### Run the transformation demo

```bash
cd 05-transformation
node server.js

# Query params arrive as strings — watch the server cast and normalise them
curl -v "http://localhost:3000/api/bookmarks?page=2&limit=20"

# Email normalisation — send mixed case, receive lowercase
curl -X POST http://localhost:3000/api/transform \
  -H "Content-Type: application/json" \
  -d '{
    "email": "Jane.DOE@GMAIL.COM",
    "phone": "9876543210",
    "date": "2025-11-05"
  }'
```

Each demo has a **"Break It On Purpose"** section — send an empty body, send the wrong types, send a date in the future, send mismatched passwords — and observe exactly what the validation pipeline returns.

---

> **Next module:** [Module 06 — Middleware](../06-middleware/) — now that you understand validation pipelines, we look at how middleware chains work and how to build reusable logic that runs between the request and your controller.
