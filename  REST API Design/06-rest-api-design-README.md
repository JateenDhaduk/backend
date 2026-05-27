# Module 06 — REST API Design

> **The core question:** Before writing a single line of code, how do you design an API that is intuitive, consistent, and delightful for whoever consumes it?

API design is one of the most important skills a backend engineer develops. The decisions you make at this stage — how to name resources, which HTTP method to use, what to return in a response — affect every engineer who ever integrates your API. This module covers REST API design from first principles: the history behind it, the rules that govern it, and a complete end-to-end walkthrough of designing a real API interface for a project management platform.

---

## Table of Contents

- [A Brief History — Where REST Came From](#a-brief-history--where-rest-came-from)
- [What "REST" Actually Means](#what-rest-actually-means)
- [The Six REST Constraints](#the-six-rest-constraints)
- [Anatomy of an API URL](#anatomy-of-an-api-url)
- [Resources — The Foundation of API Design](#resources--the-foundation-of-api-design)
- [Idempotency & HTTP Methods](#idempotency--http-methods)
- [Designing CRUD Endpoints](#designing-crud-endpoints)
  - [List Resource](#1-list-resource-with-pagination-sorting--filtering)
  - [Create Resource](#2-create-resource)
  - [Get Single Resource](#3-get-single-resource)
  - [Update Resource](#4-update-resource)
  - [Delete Resource](#5-delete-resource)
  - [Custom Actions](#6-custom-actions)
- [Response Design](#response-design)
- [The Golden Rules of API Design](#the-golden-rules-of-api-design)
- [Demos in This Module](#demos-in-this-module)
- [Getting Started](#getting-started)

---

## A Brief History — Where REST Came From

In 1990, Tim Berners-Lee created the World Wide Web to share knowledge globally. Within a year he invented URI, HTTP, HTML, the first web server, and the first web browser — technologies we still use today.

The web grew faster than anyone anticipated. The exponential growth of users threatened to collapse the entire system. In 1993, **Roy Fielding** — co-founder of the Apache HTTP Server project — identified the scalability problem and proposed a set of architectural constraints to fix it.

Fielding worked with Berners-Lee to standardise the web's design, and together they wrote the HTTP/1.1 specification. In 2000, Fielding named and formally described this architectural style in his PhD dissertation — calling it **REST: Representational State Transfer**.

> The original paper is publicly available. Searching "Roy Fielding REST dissertation" and reading it gives you the full context of why every REST convention exists.

---

## What "REST" Actually Means

The name breaks into three meaningful parts:

**Representational** — Resources (data, objects) are represented in a specific format. The same resource can have different representations for different clients:

```
User resource in the database
        │
        ├── JSON representation  → for API clients (other servers, mobile apps)
        └── HTML representation  → for web browsers
```

**State** — Each resource has a current state — its properties and values at a given moment. A shopping cart's state includes all items, quantities, and total price.

**Transfer** — The movement of resource representations between client and server through a common standard (HTTP), using methods like GET, POST, PUT, PATCH, and DELETE.

Combined: REST is an architectural style where resources are represented in different formats, their state is transferred between client and server, and the system follows specific constraints to remain scalable.

---

## The Six REST Constraints

These are the six constraints Roy Fielding proposed to make the web scalable. Every REST API you build today is built on top of these.

| Constraint | What It Means |
|---|---|
| **Client-Server** | Separate concerns — client handles UI, server handles data and business logic. Each can evolve independently. |
| **Uniform Interface** | A standardised way for all components to communicate. Includes resource identification, manipulation through representations, self-descriptive messages. |
| **Layered System** | Architecture is hierarchical. Each layer only sees the layer directly below it. Enables load balancers, proxies, and CDNs without affecting core functionality. |
| **Caching** | Responses must be labelled as cacheable or non-cacheable. Reduces server load and improves response time. |
| **Stateless** | Every request must contain all information needed to process it. The server holds no memory of past requests. |
| **Code on Demand** *(optional)* | Servers can extend client functionality by sending executable code (e.g. JavaScript). Optional — rarely used as a deliberate constraint. |

---

## Anatomy of an API URL

This is what a standard API URL looks like:

```
https://api.example.com/v1/organizations?status=active&page=2
─────┬─  ─────────────┬  ─┬  ─────────┬  ─────────────────┬
     │                │   │           │                    │
  Scheme           Domain  Version  Resource          Query params
  (https)    (api subdomain)         (plural noun)
```

**Rules for constructing API URLs:**

**1. Use the `api.` subdomain** — separates your API from your main web domain cleanly.

**2. Version your API in the path** — `/v1/`, `/v2/` etc. This allows breaking changes without breaking existing clients.

**3. Resource names are always plural nouns** — this is the rule most people get wrong. Even when fetching a single resource, the path segment stays plural:

```
✅  GET /api/v1/organizations        ← list all
✅  GET /api/v1/organizations/123    ← get one (still plural)
❌  GET /api/v1/organization/123     ← wrong — don't singularise
```

**4. Use hyphens, not underscores or spaces** — URLs travel through many environments. Hyphens are the safe choice:

```
✅  /api/v1/project-tasks
❌  /api/v1/project_tasks
❌  /api/v1/project tasks
```

**5. Use lowercase always** — avoid case-sensitivity issues across different server environments:

```
✅  /api/v1/organizations
❌  /api/v1/Organizations
```

**6. Forward slash means hierarchical relationship** — every `/` in a path segment implies that the resource on the right is nested inside the resource on the left:

```
/api/v1/organizations/123/projects/456/tasks
         ─────────┬──  ─┬  ───────┬───  ────┬
                  │     │         │          │
            all orgs  org 123  projects   task 456
                              of org 123  of project 456
```

---

## Resources — The Foundation of API Design

The first step in API design — before writing any code — is identifying your **resources**.

Resources are the nouns of your system. You find them by analysing your product requirements, wireframes, and Figma designs. Any noun that represents data your system stores or manages is a candidate resource.

**Example: Project Management Platform (Jira/Linear-style)**

After analysing requirements:

| Noun Found | Resource Name |
|---|---|
| Users of the platform | `users` |
| Companies or teams using the platform | `organizations` |
| Work containers inside an organization | `projects` |
| Individual work items inside a project | `tasks` |
| Labels that categorise tasks | `tags` |

Once resources are identified, you know:
1. What database tables to design
2. What API endpoints to create
3. How resources relate to each other hierarchically

---

## Idempotency & HTTP Methods

**Idempotency** means: performing the same operation once produces the same result as performing it a thousand times.

This concept determines which HTTP method to use for which operation.

| Method | Purpose | Idempotent? | Why |
|---|---|---|---|
| `GET` | Retrieve a resource | ✅ Yes | Fetching data causes no side effects regardless of how many times called |
| `PUT` | Replace a resource entirely | ✅ Yes | Replacing with the same payload always leaves the resource in the same state |
| `PATCH` | Partially update a resource | ✅ Yes | Applying the same partial update repeatedly produces the same end state |
| `DELETE` | Remove a resource | ✅ Yes | Resource is deleted on call #1; calls 2–1000 find nothing to delete — same outcome |
| `POST` | Create a resource or custom action | ❌ No | Each call creates a new resource with a new ID — different outcome every time |

**POST as the catch-all for custom actions:**

When an operation does not cleanly fit any CRUD method, use `POST`. The REST spec makes `POST` intentionally open-ended for exactly this reason.

```
Archiving an organization is not just a status update.
  → It deletes all projects under it
  → It sends notifications to all members
  → It cleans up associated tasks

This is a custom action → POST /organizations/:id/archive
```

---

## Designing CRUD Endpoints

Using a project management platform as the example, here is how every type of endpoint should be designed.

---

### 1. List Resource (with Pagination, Sorting & Filtering)

```
GET /api/v1/organizations
```

**Why pagination is mandatory on list APIs:**

Returning all records from a database in one response is dangerous at scale. Serializing 10,000 records into JSON is slow, heavy, and makes the client wait. Pagination returns a controlled slice of data.

**Standard pagination query parameters:**

```
GET /api/v1/organizations?page=2&limit=10
```

| Parameter | Default | Description |
|---|---|---|
| `page` | `1` | Which slice of data to return |
| `limit` | `10` (or `20`) | How many records per page |

**Standard paginated response shape:**

```json
{
  "data": [ ...array of records... ],
  "total": 50,
  "page": 2,
  "totalPages": 5
}
```

| Field | Purpose |
|---|---|
| `data` | The current page of records |
| `total` | Total count of all records in the database (for UI metadata) |
| `page` | Which page this response represents |
| `totalPages` | How many pages exist — client uses this to stop paginating |

**Standard sorting query parameters:**

```
GET /api/v1/organizations?sortBy=name&sortOrder=asc
```

| Parameter | Default | Options |
|---|---|---|
| `sortBy` | `createdAt` | Any field on the resource |
| `sortOrder` | `desc` | `asc` or `desc` |

Always set sensible sort defaults server-side. If the client passes nothing, return records sorted by `createdAt` descending (newest first) — that is the natural expectation.

**Standard filtering:**

```
GET /api/v1/organizations?status=active
GET /api/v1/organizations?status=active&name=Acme
```

Any field can be a filter parameter. The server filters results before paginating.

> **Critical rule:** A list API with no results returns `200 OK` with `data: []` — **never** `404`. The `404` code means a specific resource was not found. An empty list is a valid, successful response.

---

### 2. Create Resource

```
POST /api/v1/organizations
```

**Request body** — only fields the client provides; server-generated fields (`id`, `createdAt`, `updatedAt`) are excluded:

```json
{
  "name": "Acme Corp",
  "status": "active",
  "description": "Our main organization"
}
```

**Response** — `201 Created` with the full newly created resource:

```json
HTTP/1.1 201 Created

{
  "id": "org-123",
  "name": "Acme Corp",
  "status": "active",
  "description": "Our main organization",
  "createdAt": "2026-05-15T10:00:00Z",
  "updatedAt": "2026-05-15T10:00:00Z"
}
```

**Why `201` not `200`?** — `201` specifically communicates "a new resource was created". `200` means "the operation succeeded". They are not interchangeable.

---

### 3. Get Single Resource

```
GET /api/v1/organizations/:id
```

**Response** — `200 OK` with the resource:

```json
HTTP/1.1 200 OK

{
  "id": "org-123",
  "name": "Acme Corp",
  "status": "active",
  ...
}
```

**If the resource does not exist** — `404 Not Found`:

```json
HTTP/1.1 404 Not Found

{
  "error": "Organization not found",
  "resourceId": "org-123"
}
```

---

### 4. Update Resource

```
PATCH /api/v1/organizations/:id
```

**Use `PATCH`, not `PUT`, for most update operations.** `PATCH` signals partial update — only the fields you send change. `PUT` signals full replacement — omitted fields are deleted.

**Request body** — only the fields being updated:

```json
{
  "status": "archived"
}
```

**Response** — `200 OK` with the full updated resource:

```json
HTTP/1.1 200 OK

{
  "id": "org-123",
  "name": "Acme Corp",
  "status": "archived",
  ...
}
```

---

### 5. Delete Resource

```
DELETE /api/v1/organizations/:id
```

**Response** — `204 No Content` with an empty body:

```
HTTP/1.1 204 No Content

(empty body)
```

`204` means: "the operation succeeded, but there is nothing to return." After deletion, the resource no longer exists — sending it back would be misleading.

---

### 6. Custom Actions

When an operation does not fit cleanly into any CRUD method, use `POST` with the action name appended to the resource path.

**Archive an organization** (involves cascading operations — not just a status field update):

```
POST /api/v1/organizations/:id/archive
```

**Clone a project** (server creates a new project with the same data — but also clones all tasks and sends notifications):

```
POST /api/v1/projects/:id/clone
```

**The URL structure of a custom action:**

```
/api/v1/organizations/123/archive
         ─────────┬──  ─┬  ──┬──
                  │     │    │
            resource   id   action name
```

**Important:** Custom actions that create something return `201`. Custom actions that only trigger behaviour return `200`. Do not assume `POST` always means `201`.

---

## Response Design

### Route and Response Code Reference

| Operation | Method | Route | Success Code | Response Body |
|---|---|---|---|---|
| List all | `GET` | `/resources` | `200` | `{ data, total, page, totalPages }` |
| Get one | `GET` | `/resources/:id` | `200` | Full resource object |
| Create | `POST` | `/resources` | `201` | Newly created resource |
| Update | `PATCH` | `/resources/:id` | `200` | Updated resource |
| Full replace | `PUT` | `/resources/:id` | `200` | Replaced resource |
| Delete | `DELETE` | `/resources/:id` | `204` | Empty body |
| Custom action | `POST` | `/resources/:id/action` | `200` or `201` | Depends on what the action does |

### Error Response Shape

All error responses across your entire API should follow the same envelope:

```json
{
  "error": "Organization not found",
  "statusCode": 404,
  "timestamp": "2026-05-15T10:00:00Z"
}
```

For validation errors (`400`), include field-level detail:

```json
{
  "error": "Validation failed",
  "statusCode": 400,
  "details": [
    { "field": "name", "message": "name is required" },
    { "field": "status", "message": "status must be 'active' or 'archived'" }
  ]
}
```

### Nested Resources — Route Hierarchy

Use nested routes to express hierarchical relationships between resources:

```
GET /api/v1/organizations/:orgId/projects
    → All projects belonging to organization orgId

GET /api/v1/organizations/:orgId/projects/:projectId/tasks
    → All tasks belonging to project projectId within organization orgId
```

**Rule of thumb:** Do not nest more than 2–3 levels deep. Beyond that, URLs become unwieldy. `/orgs/:id/projects/:id/tasks/:id/comments/:id/likes` is technically valid but practically painful.

---

## The Golden Rules of API Design

### 1. Design before you code

The interface of your API is a design decision, not a programming decision. Use a tool like Insomnia, Postman, or Swagger to sketch out all your endpoints — their routes, payloads, and expected responses — before writing any server code. This forces you to think from the consumer's perspective.

### 2. Start from the UI wireframes

Your Figma designs tell you what data users need. Every noun in those designs is a potential resource. Every action a user can perform is a potential endpoint.

### 3. Be consistent across all resources

If `description` is a field in one resource's JSON payload, use `description` everywhere — not `desc`, not `details`, not `info`. If your list APIs return `{ data, total, page, totalPages }`, every list API in your entire system must follow that shape.

Inconsistency forces API consumers to read documentation for every single endpoint instead of leveraging patterns they already know from your other endpoints.

### 4. Provide sensible defaults

The client should never be required to send "obvious" values:

```
Page not specified   → default to page 1
Limit not specified  → default to 10 or 20
Sort not specified   → default to createdAt descending
Status on create     → default to "active"
```

The fewer required fields, the better the developer experience. Only require what you absolutely cannot infer.

### 5. Never use abbreviations in field names

```
✅  description
❌  desc

✅  organizationId
❌  orgId

✅  createdAt
❌  cAt
```

The person integrating your API six months from now does not share your mental context. Full, readable field names eliminate ambiguity entirely.

### 6. Always write interactive documentation

Swagger (OpenAPI) is the standard. Set it up from day one, keep it updated with every change, and treat it as a first-class deliverable — not an afterthought. It serves as both documentation and a live playground for testing.

---

## Demos in This Module

| # | Demo | Concept |
|---|---|---|
| 01 | [URL Structure](./01-url-structure/) | Scheme, subdomain, versioning, resource naming, path hierarchy |
| 02 | [List API](./02-list-api/) | Pagination, sorting, filtering — full implementation |
| 03 | [CRUD Endpoints](./03-crud-endpoints/) | Create, Read, Update, Delete for a resource — status codes and response shapes |
| 04 | [Nested Routes](./04-nested-routes/) | Hierarchical resource relationships in URL paths |
| 05 | [Custom Actions](./05-custom-actions/) | POST-based non-CRUD operations — archive, clone, publish |
| 06 | [Response Design](./06-response-design/) | Consistent success and error response envelopes |
| 07 | [Full API Design](./07-full-api-design/) | Complete end-to-end API interface for a project management platform |

---

## Getting Started

### Prerequisites

- Node.js v18+ (or Java 17+ for Spring Boot demos)
- [Insomnia](https://insomnia.rest/) or [Postman](https://www.postman.com/) for designing and testing the API interface

### Run the full demo server

```bash
git clone [repository-url]
cd [repository-name]/06-rest-api-design

cd 07-full-api-design
npm install
node server.js

# The server exposes a complete project management API
# Explore it through Insomnia or curl

# List all organizations (empty at first)
curl http://localhost:3000/api/v1/organizations

# Create an organization
curl -X POST http://localhost:3000/api/v1/organizations \
  -H "Content-Type: application/json" \
  -d '{"name": "Acme Corp", "description": "Our main org"}'

# Paginate, sort, and filter
curl "http://localhost:3000/api/v1/organizations?page=1&limit=2&sortBy=name&sortOrder=asc&status=active"

# Trigger a custom action
curl -X POST http://localhost:3000/api/v1/organizations/ORG_ID/archive
```

Each demo's `README.md` contains the curl commands, what to observe in each response, and a **"Break It On Purpose"** section — use the wrong HTTP method, omit required fields, try to GET a deleted resource, and observe exactly what the well-designed API tells you.

---

> **Next module:** [Module 07 — Databases](../07-databases/) — your API design is complete. Now we look at how to model, store, and query the data your APIs serve.
