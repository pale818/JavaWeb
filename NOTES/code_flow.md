# Online Shop — Code Flow & Class Reference

## Live Application
**URL:** https://web-production-8a929.up.railway.app

---

## PayPal Sandbox Accounts

The application uses PayPal **sandbox** (test mode) — no real money is charged.

| Role | Where to find it |
|---|---|
| **Merchant** (receives money) | developer.paypal.com → Testing Tools → Sandbox Accounts → Business account |
| **Buyer** (pays during checkout) | developer.paypal.com → Testing Tools → Sandbox Accounts → Personal account → View/Edit → copy email + password |

When testing PayPal checkout, log in with the **Personal (buyer)** sandbox account credentials when PayPal redirects you for payment approval.

---

## Application Startup Flow

When the Spring Boot application starts, the following happens in order:

1. **Spring context loads** — all `@Component`, `@Service`, `@Repository`, `@Controller` beans are created and wired together.
2. **SecurityConfiguration** sets up two separate security filter chains (JWT chain for `/api/**`, form-login chain for everything else).
3. **DataInitializer** (`ApplicationRunner`) runs automatically after the context is ready:
   - Creates `ROLE_ADMIN` and `ROLE_CUSTOMER` roles if they don't exist yet.
   - Creates `admin` and `customer` users with BCrypt-encoded passwords if they don't exist.
   - Seeds 3 categories (Electronics, Books, Clothing) and 4 products if the database is empty.
4. **H2 database** is available at `/h2-console` — schema is managed by Hibernate (`ddl-auto=update`).
5. The app listens on the port provided by the `PORT` environment variable (Railway sets this), falling back to `8080` locally.

---

## Security Architecture

There are **two completely separate security filter chains**, both defined in `SecurityConfiguration`.

### Chain 1 — JWT / REST (Order 1, matches `/api/**`)
- **Stateless** — no session, no cookies.
- Every request to `/api/**` goes through `JwtRequestFilter` which reads the `Authorization: Bearer <token>` header.
- If the token is valid, the user is authenticated for that request only.
- Public endpoints: `/api/auth/**` (login), `/api/products/**` (search).
- Protected endpoint: everything else under `/api/**` requires a valid JWT.

### Chain 2 — MVC / Form Login (Order 2, matches everything else)
- **Session-based** — uses `JSESSIONID` cookie stored in the browser.
- Login form at `/login` → Spring Security validates credentials → sets `JSESSIONID` in browser cookie → user stays logged in for the session duration.
- Public: `/`, `/categories/**`, `/products/**`, `/cart/**`, `/login`.
- Customer-only: `/checkout/**`, `/orders/**`.
- Admin-only: `/admin/**`.

### How the two chains coexist
A request to `/api/products/search` goes to Chain 1 (JWT, stateless).
A request to `/categories` goes to Chain 2 (session, form login).
They never interfere with each other.

---

## Package Structure & Class Explanations

### `configuration/`

**`SecurityConfiguration.java`**
Defines the two filter chains described above. Also declares:
- `PasswordEncoder` bean (BCrypt) — used to hash passwords before saving to DB.
- `DaoAuthenticationProvider` bean — wires `MyUserDetailsService` + `PasswordEncoder` so Spring knows how to validate login credentials.
- `AuthenticationManager` bean — used by `AuthRestController` to authenticate REST login requests.

**`JwtUtil.java`**
Utility component for JWT tokens:
- `generateToken(username)` — creates a signed JWT valid for 24 hours (`jwt.expiration=86400000` ms).
- `extractUsername(token)` — reads the `subject` claim from the token.
- `isTokenValid(token)` — checks the signature and expiry date.
- Uses HMAC-SHA256 signing with a secret key from `application.properties`.

**`DataInitializer.java`**
Implements `ApplicationRunner` — runs once after the application starts. Seeds all required test data (roles, users, categories, products) using repository calls. Passwords are read from `app.default-password` property (injected via `@Value`) and encoded with BCrypt. Guards against re-seeding with `count() > 0` checks.

---

### `model/`  *(JPA entities — mapped to database tables)*

**`User.java`** — table `users`. Fields: `id`, `username`, `email`, `password` (BCrypt hash), `roles` (many-to-many with `Role`).

**`Role.java`** — table `roles`. Fields: `id`, `name` (`ROLE_ADMIN` or `ROLE_CUSTOMER`). Spring Security requires the `ROLE_` prefix.

**`Category.java`** — table `categories`. Fields: `id`, `name`, `description`. Has a one-to-many relationship to `Product`.

**`Product.java`** — table `products`. Fields: `id`, `name`, `description`, `price`, `stockQuantity`, `category` (many-to-one FK to `categories`).

**`Order.java`** — table `orders`. Fields: `id`, `user` (FK), `createdAt`, `paymentMethod` (`CASH` or `PAYPAL`), `totalAmount`, `items` (one-to-many to `OrderItem`).

**`OrderItem.java`** — table `order_items`. Fields: `id`, `order` (FK), `product` (FK), `quantity`, `priceAtPurchase` (price snapshot at time of purchase, so historical prices are preserved even if the product price changes later).

**`LoginHistory.java`** — table `login_history`. Fields: `id`, `username`, `ipAddress`, `loginAt`. Written by the `AuthenticationSuccessListener` on every successful login.

---

### `repository/`  *(Spring Data JPA — auto-generates SQL)*

All repositories extend `JpaRepository` which provides `findAll()`, `findById()`, `save()`, `deleteById()` for free.

| Repository | Notable custom methods |
|---|---|
| `UserRepository` | `findByUsername(String)` |
| `RoleRepository` | `findByName(String)` |
| `CategoryRepository` | — |
| `ProductRepository` | `findByCategoryId(Long)`, `findByNameStartingWithIgnoreCaseAndCategoryId(String, Long)` |
| `OrderRepository` | `findByUserId(Long)`, `findAll(Specification)` for filtered search |
| `OrderItemRepository` | — |
| `LoginHistoryRepository` | `findAll()` |

---

### `service/`

**`MyUserDetailsService.java`**
Implements Spring Security's `UserDetailsService`. Called by Spring Security during login to load user details from the database by username. Converts `User` entity roles into Spring `GrantedAuthority` objects.

**`CartService.java`**
`@SessionScope` — a separate instance exists per HTTP session (per logged-in browser tab). Internally holds a `Map<Long, Integer>` (productId → quantity). Methods: `addItem`, `updateQuantity`, `removeItem`, `clear`, `getItems`, `getTotalItemsCount`. Because it's session-scoped, the cart is tied to the browser session and cleared when the session ends.

**`CategoryService` / `CategoryServiceImpl`**
Simple CRUD delegation to `CategoryRepository`. Methods: `getAllCategories`, `getCategoryById`, `save`, `deleteById`.

**`ProductService` / `ProductServiceImpl`**
CRUD + search delegation to `ProductRepository`. Methods: `getAllProducts`, `getProductById`, `getProductsByCategory`, `searchByName`, `searchByNameInCategory`, `save`, `deleteById`.

**`OrderService` / `OrderServiceImpl`**
Order management. `createOrder` saves the full order with items. `searchOrders` uses JPA Specifications to filter by username and/or date range dynamically. `getOrdersByUser` returns a customer's own order history.

**`PayPalService.java`**
Handles communication with PayPal Orders API v2 (sandbox):
1. `getAccessToken()` — calls `POST /v1/oauth2/token` with client credentials (Basic Auth), returns a short-lived bearer token.
2. `createOrder(amount)` — calls `POST /v2/checkout/orders`, sends the amount in EUR plus `return_url` and `cancel_url`. PayPal returns an order object with a list of links — the method extracts the `approve` link and returns it. The controller then redirects the browser to that URL.
3. `captureOrder(paypalOrderId)` — calls `POST /v2/checkout/orders/{id}/capture` after the user approves payment on PayPal's site. This finalises the payment.

---

### `dto/`  *(Data Transfer Objects — not JPA entities)*

**`LoginRequest.java`** — record with `username` and `password` fields. Used as `@RequestBody` in the REST login endpoint.

**`ProductDTO.java`** — used as the response body for REST API endpoints (`/api/products/**`). Contains only the fields the API client needs, without JPA annotations.

**`CategoryForm.java`** — used as `@ModelAttribute` in admin category save form. Decouples the HTML form from the JPA entity (SonarQube S4684 rule).

**`ProductForm.java`** — same idea for products. Contains `categoryId` (a `Long`) instead of a full `Category` object, which is what an HTML `<select>` naturally submits.

---

### `filter/`

**`RequestResponseLoggingFilter.java`** *(Security Filter — LO5)*
Extends `OncePerRequestFilter`, runs on every HTTP request. After the request is processed, logs: `[METHOD] /path → HTTP_STATUS` using SLF4J. This satisfies the "security filter" exam requirement.

**`JwtRequestFilter.java`**
Also extends `OncePerRequestFilter`. Reads the `Authorization` header. If it contains a valid `Bearer` token, extracts the username, loads the `UserDetails`, and sets the authentication in `SecurityContextHolder` so Spring Security treats the request as authenticated for its duration.

---

### `listener/`

**`AuthenticationSuccessListener.java`** *(Application Listener — LO6)*
Implements `ApplicationListener<AuthenticationSuccessEvent>`. Spring publishes this event automatically after every successful login. The listener:
1. Reads the username from the authentication object.
2. Reads the IP address from `WebAuthenticationDetails`.
3. Saves a `LoginHistory` record to the database.
This is visible to admins at `/admin/login-history`.

---

### `specification/`

**`OrderSpecification.java`**
Contains static factory methods that return JPA `Specification<Order>` objects — these are composable predicates that translate to SQL `WHERE` clauses at runtime:
- `hasUsername(username)` → `WHERE LOWER(user.username) LIKE %username%`
- `createdAfter(from)` → `WHERE created_at >= from_date 00:00:00`
- `createdBefore(to)` → `WHERE created_at <= to_date 23:59:59`

Used by `OrderServiceImpl.searchOrders()` to build a dynamic query without writing SQL.

---

### `controller/mvc/`  *(Return HTML views via Thymeleaf)*

**`PublicMvcController`** — anonymous-accessible pages: home `/`, category list `/categories`, products by category `/categories/{id}/products`, product detail `/products/{id}`, login page `/login`.

**`CartMvcController`** — `/cart` (view), `/cart/add`, `/cart/update`, `/cart/remove`, `/cart/clear`. All anonymous. Cart state lives in the session-scoped `CartService`.

**`CheckoutMvcController`** — requires `CUSTOMER` role:
- `GET /checkout` — shows cart contents and payment options.
- `POST /checkout` — if CASH: saves order immediately, shows confirmation. If PAYPAL: calls `PayPalService.createOrder()`, redirects browser to PayPal approval URL.
- `GET /checkout/paypal/return?token=` — PayPal redirects here after approval. Captures the payment, saves the order, shows confirmation.
- `GET /checkout/paypal/cancel` — PayPal redirects here if the user cancels; redirects back to checkout page.

**`CustomerMvcController`** — requires `CUSTOMER` role. `GET /orders/my` — loads the logged-in customer's order history from the database and renders `my-orders.html`.

**`AdminMvcController`** — requires `ADMIN` role. Full CRUD for categories and products (list, new form, edit form, save, delete). Login history view. All orders view with search filters by customer username and date range.

---

### `controller/rest/`  *(Return JSON — JWT protected)*

**`AuthRestController`** — `POST /api/auth/login`. Accepts `{"username":"...","password":"..."}`, authenticates via `AuthenticationManager`, generates a JWT, returns `{"token":"..."}`. Used to demonstrate JWT-based REST API security.

**`ProductRestController`** — `GET /api/products` (all), `GET /api/products/{id}` (one), `GET /api/products/search?name=&categoryId=` (AJAX search). The search endpoint is called by the JavaScript in `products.html` as the user types — this is the **asynchronous mechanism (AJAX)** exam requirement.

---

## Asynchronous Mechanism — AJAX Product Search

When a user is browsing a category page (`/categories/{id}/products`), the search box at the top uses JavaScript `fetch()` to call `/api/products/search?name=X&categoryId=Y` without reloading the page. The REST endpoint returns JSON, and JavaScript dynamically replaces the product cards on the page. This is the "asynchronous mechanism for publishing data on the web page" requirement.

---

## Request Flow Examples

### Anonymous user browses categories
```
Browser → GET /categories
  → Chain 2 (MVC): permitted for all
  → PublicMvcController.categories()
  → CategoryService.getAllCategories() → CategoryRepository → H2 DB
  → Thymeleaf renders categories.html
  → Browser displays page
```

### Customer places a PayPal order
```
Browser → POST /checkout {paymentMethod=PAYPAL}
  → Chain 2: requires ROLE_CUSTOMER ✓
  → CheckoutMvcController.placeOrder()
  → PayPalService.getAccessToken() → PayPal API → bearer token
  → PayPalService.createOrder(total) → PayPal API → approval URL
  → redirect to paypal.com/checkoutnow?token=...

User approves on PayPal →
Browser → GET /checkout/paypal/return?token=XXX
  → PayPalService.captureOrder(token) → PayPal API → payment captured
  → buildAndSaveOrder() → OrderRepository → H2 DB
  → CartService.clear()
  → Thymeleaf renders order-confirmation.html
```

### REST API login + authenticated call (JWT demo)
```
curl POST /api/auth/login {"username":"admin","password":"password"}
  → Chain 1 (JWT): /api/auth/** is public
  → AuthRestController.login()
  → AuthenticationManager validates credentials
  → JwtUtil.generateToken("admin") → signed JWT
  → Response: {"token":"eyJ..."}

curl GET /api/products -H "Authorization: Bearer eyJ..."
  → Chain 1 (JWT): JwtRequestFilter reads token
  → JwtUtil.isTokenValid() ✓, extractUsername() → "admin"
  → SecurityContext populated
  → ProductRestController.getAll() → JSON response
```

### Admin logs in (listener fires)
```
Browser → POST /login {username=admin, password=password}
  → Chain 2: DaoAuthenticationProvider validates against DB
  → Spring publishes AuthenticationSuccessEvent
  → AuthenticationSuccessListener.onApplicationEvent()
      → reads username="admin", ip="x.x.x.x"
      → LoginHistoryRepository.save(LoginHistory)
  → redirect to / (defaultSuccessUrl)
```

---

## The Two Login Systems — Simple Comparison

| | Chain 1 — JWT (REST) | Chain 2 — Form Login (MVC) |
|---|---|---|
| **Who uses it** | API clients (curl, mobile, JS fetch) | Browser users (HTML form) |
| **Credentials sent** | JSON body `{"username":"...","password":"..."}` | HTML form POST `username=...&password=...` |
| **What authenticates the request** | `JwtRequestFilter` reads the `Authorization` header | `UsernamePasswordAuthenticationFilter` reads the form POST |
| **What you get back after login** | A JWT token string | A `JSESSIONID` cookie |
| **How future requests prove identity** | Send `Authorization: Bearer <token>` header every time | Browser sends `JSESSIONID` cookie automatically |
| **Server remembers you between requests** | No — stateless, token carries everything | Yes — session stored on server, cookie is just the key |
| **Token/session expires** | JWT expires after 24 hours | Session ends when browser closes or `/logout` is called |

The short version: **JWT = token in a header, no server memory. Form login = cookie pointing to server-side session.**

---

## JWT Login Flow — Step by Step

This is the full request lifecycle for REST API login in **Chain 1** (stateless, `/api/**`).

### Step 1 — Client sends login request
```
POST /api/auth/login
Content-Type: application/json
{"username": "admin", "password": "password"}
```
This hits `/api/auth/**` which is `permitAll()` in Chain 1 — no authentication required to reach the login endpoint itself.

---

### Step 2 — No filter blocks the request
`JwtRequestFilter` runs but finds no `Authorization` header on this request, so it does nothing and passes the request through to the controller.

---

### Step 3 — Controller authenticates manually
```
AuthRestController.login()
  → AuthenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken("admin", "password")
    )
    → DaoAuthenticationProvider (same one used by Chain 2)
        → MyUserDetailsService.loadUserByUsername("admin")
            → UserRepository.findByUsername("admin") → User entity from H2
        → BCryptPasswordEncoder.matches(rawPassword, storedHash)
            → if match: authentication succeeds
            → if no match: throws BadCredentialsException → 401 Unauthorized
```
The difference from Chain 2: here the controller calls `AuthenticationManager` directly in Java code. In Chain 2, `UsernamePasswordAuthenticationFilter` does this automatically.

---

### Step 4 — JWT token is generated and returned
```
JwtUtil.generateToken("admin")
  → builds JWT with subject="admin", issued-at=now, expiry=now+24h
  → signs it with HMAC-SHA256 using the secret key from application.properties
  → returns token string "eyJ..."

Response: 200 OK
{"token": "eyJhbGciOiJIUzI1NiJ9..."}
```
The client stores this token (e.g. in memory or localStorage).

---

### Step 5 — Client makes authenticated requests
```
GET /api/products
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```
Every subsequent request must include the token in the header. There is no cookie — the client is responsible for sending the token each time.

---

### Step 6 — JwtRequestFilter validates the token
```
JwtRequestFilter.doFilterInternal()
  → reads Authorization header → extracts token after "Bearer "
  → JwtUtil.isTokenValid(token)
      → verifies HMAC-SHA256 signature (tamper check)
      → checks expiry date
  → JwtUtil.extractUsername(token) → "admin"
  → MyUserDetailsService.loadUserByUsername("admin") → UserDetails
  → SecurityContextHolder.getContext().setAuthentication(auth)
      → request is now authenticated for its duration
  → filterChain.doFilter() → request continues to controller
```
No session is created. The `SecurityContext` is populated fresh on every single request from the token alone.

---

### Step 7 — Controller handles the request normally
```
ProductRestController.getAll()
  → ProductService.getAllProducts() → ProductRepository → H2
  → returns List<ProductDTO> → serialized to JSON
  → 200 OK
```

---

### JWT Login — Class Map

| Step | Class / Component | Responsibility |
|---|---|---|
| Receive login request | `AuthRestController` | Calls `AuthenticationManager` directly |
| Load user from DB | `MyUserDetailsService` | `loadUserByUsername()` → `UserRepository` |
| Verify password | `DaoAuthenticationProvider` + `BCryptPasswordEncoder` | Hash comparison |
| Generate token | `JwtUtil.generateToken()` | Creates signed JWT, 24h expiry |
| Validate token per request | `JwtRequestFilter` | Reads header, validates JWT, sets `SecurityContext` |
| Enforce roles | `AuthorizationFilter` | Checks `ROLE_ADMIN` / `ROLE_CUSTOMER` |

---

## MVC Login Flow — Step by Step

This is the full request lifecycle for a form-based login in **Chain 2** (session/cookie, everything that is NOT `/api/**`).

### Step 1 — User loads the login page
```
Browser → GET /login
  → Chain 2: /login is explicitly permitted for all (no auth required)
  → PublicMvcController.loginPage() returns view name "login"
  → ThymeleafViewResolver resolves "login" → classpath:/templates/login.html
  → Thymeleaf renders the form: <form th:action="@{/login}" method="post">
  → Browser displays username + password fields
```
The form `action` is `/login` and `method` is `POST`. Spring Security owns this URL — there is **no controller method** handling `POST /login`.

---

### Step 2 — User submits credentials
```
Browser → POST /login  (form-encoded body: username=admin&password=password)
```
Spring Security's `UsernamePasswordAuthenticationFilter` intercepts this request **before** it ever reaches any controller. It extracts `username` and `password` from the request parameters.

> **`UsernamePasswordAuthenticationFilter` vs `UsernamePasswordAuthenticationToken` — don't confuse them:**
>
> - **`UsernamePasswordAuthenticationFilter`** is the *filter*. It only exists in Chain 2 (added automatically by `.formLogin()`). It intercepts `POST /login` and pulls credentials out of the form. Chain 1 never has this filter.
>
> - **`UsernamePasswordAuthenticationToken`** is a *data object* (implements `Authentication`). Chain 1 uses it in two places:
>   1. In `AuthRestController` — `new UsernamePasswordAuthenticationToken(username, password)` — credentials included, passed to `AuthenticationManager` as a login attempt.
>   2. In `JwtRequestFilter` — `new UsernamePasswordAuthenticationToken(userDetails, null, authorities)` — null credential, meaning "already authenticated", placed directly into `SecurityContextHolder`.
>
> The `.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class)` line in `SecurityConfiguration` uses the filter class only as a **position marker** — it does not add that filter to Chain 1.

---

### Step 3 — Authentication via DaoAuthenticationProvider
```
UsernamePasswordAuthenticationFilter
  → creates UsernamePasswordAuthenticationToken(username, password)
  → hands it to AuthenticationManager (ProviderManager)
    → delegates to DaoAuthenticationProvider (configured in SecurityConfiguration)
      → calls MyUserDetailsService.loadUserByUsername("admin")
          → UserRepository.findByUsername("admin") → User entity from H2
          → builds Spring Security UserDetails with username, BCrypt hash, roles
      → BCryptPasswordEncoder.matches(rawPassword, storedHash)
          → if match: authentication succeeds
          → if no match: throws BadCredentialsException
```
`DaoAuthenticationProvider` is wired in `SecurityConfiguration` with `MyUserDetailsService` + `BCryptPasswordEncoder`. It never stores or compares plain-text passwords — only the BCrypt hash stored in the DB.

---

### Step 4a — Login SUCCESS
```
AuthenticationSuccessHandler (Spring default: SavedRequestAwareAuthenticationSuccessHandler)
  → populates SecurityContext: SecurityContextHolder.getContext().setAuthentication(auth)
  → saves SecurityContext to the HTTP session (HttpSession)
  → creates JSESSIONID cookie → sent to browser in Set-Cookie header
  → Spring publishes AuthenticationSuccessEvent
      → AuthenticationSuccessListener.onApplicationEvent()
          → reads username + IP from WebAuthenticationDetails
          → LoginHistoryRepository.save(new LoginHistory(...)) → written to H2
  → redirect 302 → / (defaultSuccessUrl configured in SecurityConfiguration)
```
From this point on, every subsequent request from this browser includes the `JSESSIONID` cookie. `SecurityContextPersistenceFilter` (or `HttpSessionSecurityContextRepository`) restores the `SecurityContext` from the session on each request — this is how the user stays logged in without re-entering credentials.

---

### Step 4b — Login FAILURE (wrong password)
```
BadCredentialsException thrown inside DaoAuthenticationProvider
  → AuthenticationFailureHandler
  → redirect 302 → /login?error
  → Browser → GET /login?error
      → PublicMvcController.loginPage() — Thymeleaf checks th:if="${param.error}"
      → renders error message: "Invalid username or password."
  → SecurityContext remains empty, no JSESSIONID set
```

---

### Step 5 — Accessing a protected page after login
```
Browser → GET /admin/categories  (JSESSIONID cookie automatically included)
  → Chain 2: SecurityContextPersistenceFilter reads JSESSIONID
      → restores SecurityContext from session (user is still "admin")
  → AuthorizationFilter checks: does "admin" have ROLE_ADMIN? ✓
  → DispatcherServlet dispatches to AdminMvcController.categories()
  → AdminMvcController calls CategoryService → CategoryRepository → H2
  → Thymeleaf renders admin/categories.html with the data
  → Browser displays page (user never had to log in again)
```

---

### Step 6 — Logout
```
Browser → POST /logout  (CSRF token included — Spring Security requires it for POST)
  → LogoutFilter intercepts /logout
  → SecurityContextHolder.clearContext()
  → HttpSession.invalidate() — session destroyed server-side
  → JSESSIONID cookie cleared in browser (Max-Age=0)
  → redirect 302 → /login?logout
  → Browser → GET /login?logout
      → Thymeleaf checks th:if="${param.logout}"
      → renders: "You have been logged out."
```

---

### Full MVC Login — Class Map

| Step | Class / Component | Responsibility |
|---|---|---|
| Serve login form | `PublicMvcController` | Returns `"login"` view |
| Intercept POST /login | `UsernamePasswordAuthenticationFilter` | Extracts credentials from request |
| Load user from DB | `MyUserDetailsService` | `loadUserByUsername()` → `UserRepository` |
| Verify password | `DaoAuthenticationProvider` + `BCryptPasswordEncoder` | Hash comparison |
| Store auth in session | `HttpSessionSecurityContextRepository` | Saves `SecurityContext` to `HttpSession` |
| Set browser cookie | Servlet container (Tomcat) | `Set-Cookie: JSESSIONID=...` |
| Record login event | `AuthenticationSuccessListener` | Saves `LoginHistory` to DB |
| Restore auth per request | `SecurityContextPersistenceFilter` | Reads session → rebuilds `SecurityContext` |
| Enforce roles | `AuthorizationFilter` | Checks `ROLE_ADMIN` / `ROLE_CUSTOMER` |
| Logout | `LogoutFilter` | Clears session + cookie |
