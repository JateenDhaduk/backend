# Module 02 — Routing for Backend Engineers

> **The core question:** Once a request arrives at your server, how does it know which piece of code to run?

If HTTP methods express the **what** of a request — your intent — then routing expresses the **where**. Together, they form a unique key the server uses to map every incoming request to exactly one handler. Understanding routing is what lets you read any API, debug any 404, and design endpoint structures that other engineers can understand at a glance.

---

## Table of Contents

- [What Is Routing?](#what-is-routing)
- [Types of Routes](#types-of-routes)
  - [1. Static Routes](#1-static-routes)
  - [2. Dynamic Routes & Path Parameters](#2-dynamic-routes--path-parameters)
  - [3. Query Parameters](#3-query-parameters)
  - [4. Nested Routes](#4-nested-routes)
  - [5. Route Versioning & Deprecation](#5-route-versioning--deprecation)
  - [6. Catch-All Routes](#6-catch-all-routes)
- [The Method + Route = Unique Key Rule](#the-method--route--unique-key-rule)
- [Path Parameters vs Query Parameters](#path-parameters-vs-query-parameters)
- [Demos in This Module](#demos-in-this-module)
- [Getting Started](#getting-started)

---

## What Is Routing?

Routing is the process of **mapping a URL path to a server-side handler** — a function or block of code that processes the request and returns a response.

Think of it in two parts:

```
HTTP Method  +  URL Path  =  Handler
─────────────────────────────────────
GET          +  /api/books  =  "Return all books from the database"
POST         +  /api/books  =  "Create a new book record"
DELETE       +  /api/books/123 =  "Delete the book with id 123"
```

The method expresses your **intent**. The route expresses the **target**. The server combines both to find the one handler that should run — and only that one.

---

## Types of Routes

### 1. Static Routes

A **static route** is a fixed string that never changes. No variables, no dynamic segments — the path is always identical regardless of who makes the request or what data they're working with.

```
GET  /api/books       → Returns all books
POST /api/books       → Creates a new book
GET  /api/users       → Returns all users
```

**Why "static"?** Because `/api/books` is a constant. It always means the same resource. The server matches it character by character.

**When to use:** Any endpoint that operates on a collection of resources rather than a specific one.

```
Request:   GET /api/books
           ↓
Server:    matches GET + /api/books
           ↓
Handler:   query database → return all books
           ↓
Response:  200 OK  [ { id: 1, title: "..." }, { id: 2, ... } ]
```

---

### 2. Dynamic Routes & Path Parameters

A **dynamic route** has one or more variable segments in the path. These variable segments are called **path parameters** (also called route parameters).

```
GET /api/users/123     → Returns details for user with id 123
GET /api/users/456     → Returns details for user with id 456
GET /api/books/99      → Returns details for book with id 99
```

The convention across every language and framework is to prefix the variable segment with a colon `:` in the route definition:

```
Route definition:  GET /api/users/:id
Incoming request:  GET /api/users/123
                             ↑
                       id = "123" (extracted by the server)
```

> Note: route segments are always strings, even when the value looks like a number. The server extracts `"123"` as a string; your handler converts it to an integer if needed.

**The server's matching logic:**

```
Incoming: GET /api/users/123

Server checks:
  ✓ Method matches GET
  ✓ /api matches
  ✓ /users matches
  ✓ /123 → fits the :id slot  ← dynamic match
  → Route found. Run handler. id = "123"
```

**Readability is the point.** `GET /api/users/123` reads like plain English: *"Get me the data for the user whose ID is 123."* This human-readable quality is central to why REST APIs are designed this way.

---

### 3. Query Parameters

Query parameters are **key-value pairs appended to a URL after a `?`**. They let you pass additional metadata with a request — especially useful for GET requests, which have no body.

```
GET /api/books?page=2&limit=20&sort=asc
         ↑         ↑            ↑
     base path   page param   sort param
```

**Anatomy of a query string:**

```
/api/search?query=clean+architecture&language=java
            ↑                        ↑
         key=value              &key=value
         (first param)          (additional params)
```

**Common use cases:**

| Use Case | Example |
|---|---|
| Pagination | `/api/books?page=2&limit=20` |
| Search / filtering | `/api/books?author=Martin&genre=tech` |
| Sorting | `/api/users?sort=createdAt&order=desc` |
| Feature flags | `/api/feed?version=beta` |

**Why not just put this in the path?** You could — but it breaks the semantic meaning of the route. Consider:

```
❌ /api/search/clean+architecture/java   ← hard to read, hard to extend
✅ /api/search?query=clean+architecture&language=java  ← clear key-value intent
```

Path parameters express **identity** (which resource). Query parameters express **modifiers** (how to filter, sort, or page that resource).

**Pagination example in full:**

```
First request (no params needed — defaults apply):
  GET /api/books
  Response: { data: [...20 books...], total: 100, page: 1, totalPages: 5 }

Second request (client asks for page 2):
  GET /api/books?page=2&limit=20
  Response: { data: [...next 20 books...], total: 100, page: 2, totalPages: 5 }
```

---

### 4. Nested Routes

Nested routes express **hierarchical relationships between resources**. They are not a different routing mechanism — they are a REST API design convention that uses path parameters at multiple levels.

```
/api/users                       → All users
/api/users/123                   → User with id 123
/api/users/123/posts             → All posts by user 123
/api/users/123/posts/456         → Post 456 written by user 123
```

Each level of nesting adds a layer of semantic meaning:

```
/api/users/:userId/posts/:postId
     ↑               ↑
  whose data?     which post?
```

**How the server reads this:**

```
GET /api/users/123/posts/456

Plain English: "Get me the post whose id is 456,
               written by the user whose id is 123."
```

**Each partial path is also a valid route with its own handler:**

```
GET /api/users             → list all users
GET /api/users/123         → get user 123
GET /api/users/123/posts   → get all posts by user 123
GET /api/users/123/posts/456 → get post 456 by user 123
```

The server maps each combination to a separate handler. The nesting is what gives APIs their readable, self-describing quality.

> **Rule of thumb:** Don't nest deeper than 2–3 levels. Beyond that, the URLs become unwieldy and hard to maintain. `/api/users/123/posts/456/comments/789/likes` is technically correct but practically painful.

---

### 5. Route Versioning & Deprecation

Route versioning is the practice of **embedding a version identifier in the URL path**, allowing you to introduce breaking changes to your API without breaking existing clients.

```
GET /api/v1/products    → Original format  { id, name, price }
GET /api/v2/products    → New format       { id, title, price, currency }
```

**Why this matters:**

Imagine you built a REST API serving a web app. Six months later, a mobile app team needs the same data in a different shape. Without versioning, you face a painful choice:

- Change the existing endpoint and break the web app
- Create a new route with a different name (`/api/products-new` — messy)

With versioning, you do neither. You introduce `/api/v2/products` alongside `/api/v1/products`.

**The versioning and deprecation workflow:**

```
┌─────────────────────────────────────────────────────────────┐
│  Phase 1: Announce                                          │
│  → v2 is released. v1 is deprecated but still running.     │
│  → Notify all client teams: "Migrate by [date]"            │
├─────────────────────────────────────────────────────────────┤
│  Phase 2: Migration window                                  │
│  → Both v1 and v2 run simultaneously                       │
│  → Clients migrate at their own pace within the window     │
├─────────────────────────────────────────────────────────────┤
│  Phase 3: Shutdown                                          │
│  → v1 is removed                                           │
│  → v2 is renamed/promoted (or kept as v2)                  │
└─────────────────────────────────────────────────────────────┘
```

**Common versioning strategies:**

| Strategy | Example | Notes |
|---|---|---|
| URL path versioning | `/api/v1/users` | Most common — explicit and visible |
| Header versioning | `API-Version: 2` | Cleaner URLs, harder to test in browser |
| Query param versioning | `/api/users?version=2` | Simple but mixes identity and metadata |

URL path versioning is the most widely used because it is immediately visible in every request — in logs, in browser DevTools, and in documentation.

---

### 6. Catch-All Routes

A catch-all route is a **fallback handler** that matches any request that did not match any defined route. It is always registered last in the routing configuration.

```
Route definitions (in order):
  GET  /api/books          → books handler
  GET  /api/books/:id      → single book handler
  POST /api/books          → create book handler
  ...all other routes...
  *    /*                  → catch-all handler  ← registered last
```

**What it does:**

Without a catch-all, an unmatched request typically results in an empty response or a raw server error — not useful to the client. The catch-all ensures every request gets a meaningful response.

```
Request:   GET /api/v3/products    (server doesn't have v3)
           ↓
Server:    no route matches
           ↓
Catch-all: fires
           ↓
Response:  404 Not Found
           { "error": "The route /api/v3/products does not exist" }
```

**Why it matters:** A properly handled 404 tells the client clearly what went wrong. An unhandled request that returns nothing leaves the client guessing whether the server is down, the URL is wrong, or a network issue occurred.

---

## The Method + Route = Unique Key Rule

This is the most important rule in routing. Two routes with the **same path but different methods** are completely separate handlers:

```
GET  /api/books  ─────→  "Return all books"   (read)
POST /api/books  ─────→  "Create a new book"  (write)
```

These will **never clash**. The method is part of the key. The server checks the method first, then the path.

```
Incoming request: POST /api/books

Server logic:
  ✓ Method = POST
  ✓ Path   = /api/books
  → Unique key: POST:/api/books
  → Run the "create book" handler
```

This is what makes it possible to have a `GET /api/books` and a `POST /api/books` at the same URL without any ambiguity.

---

## Path Parameters vs Query Parameters

This is a common source of confusion. Here is the clear distinction:

| | Path Parameters | Query Parameters |
|---|---|---|
| **Position** | Inside the path, after `/` | After `?` at the end of the URL |
| **Format** | `/api/users/:id` | `/api/users?role=admin` |
| **Purpose** | Identify **which** resource | Modify **how** to return the resource |
| **Required?** | Usually yes | Usually optional |
| **Use for** | IDs, slugs, resource identity | Filters, sorting, pagination, search |

**The question to ask yourself:**

> "Am I identifying a specific resource, or am I describing how I want the resource returned?"

- Specific resource → path parameter → `/api/users/123`
- How to return it → query parameter → `/api/users?sort=name&order=asc`

---

## Demos in This Module

| # | Demo | Concept |
|---|---|---|
| 01 | [Static Routes](./01-static-routes/) | Fixed paths, method-based handler separation |
| 02 | [Dynamic Routes](./02-dynamic-routes/) | Path parameters, `:id` convention |
| 03 | [Query Parameters](./03-query-parameters/) | Pagination, filtering, sorting via query string |
| 04 | [Nested Routes](./04-nested-routes/) | Hierarchical resource relationships |
| 05 | [Route Versioning](./05-route-versioning/) | v1/v2 side-by-side, deprecation workflow |
| 06 | [Catch-All Routes](./06-catch-all-routes/) | Fallback handler, user-friendly 404s |

---

## Getting Started

### Prerequisites

- Node.js v18+ (or your preferred runtime)
- A REST client: [curl](https://curl.se/), [Postman](https://www.postman.com/), or [Burp Suite](https://portswigger.net/burp/communitydownload)

### Run any demo

```bash
# Clone the repo
git clone [repository-url]
cd [repository-name]/02-routing

# Navigate to a specific demo
cd 01-static-routes

# Start the server (no dependencies required)
node server.js

# In a separate terminal, fire requests
curl http://localhost:3000/api/books
curl -X POST http://localhost:3000/api/books \
  -H "Content-Type: application/json" \
  -d '{"title": "Clean Code"}'
```

Each demo's `README.md` contains the exact curl commands to run, what to observe in the response, and a **"Break It On Purpose"** section that helps you learn from failure.

---

> **Next module:** [Module 03 — REST API Design](../03-rest-api-design/) — now that you know how routing works mechanically, we'll learn how to design routes that are clean, consistent, and intuitive for any engineer who reads them.
