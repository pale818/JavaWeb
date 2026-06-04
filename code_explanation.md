# Code Explanation — Online Shop

## What starts first when you run the app

```
1. JVM starts
       └─ runs main() in OnlineShopApplication.java

2. Spring Boot takes over (SpringApplication.run())
       └─ scans all classes (@Component, @Service, @Repository, @Controller, @Configuration)
       └─ creates all beans and wires dependencies together
       └─ runs @Configuration classes
            └─ SecurityConfiguration builds TWO filter chains into memory:
                 Chain 1 — JWT/stateless for /api/**
                 Chain 2 — form login/session for everything else
       └─ runs DataInitializer (ApplicationRunner)
            └─ creates ROLE_ADMIN and ROLE_CUSTOMER if they don't exist
            └─ creates "admin" and "customer" users if they don't exist
            └─ seeds 3 categories and 4 products if the database is empty

3. Spring Boot starts embedded Tomcat
       └─ registers the filter chains into Tomcat
       └─ registers the Spring MVC DispatcherServlet into Tomcat

4. Tomcat starts listening on the PORT environment variable (Railway) or 8080 (local)
       └─ app prints "Started OnlineShopApplication in X seconds"
       └─ ready to accept HTTP requests
```

Tomcat is embedded inside Spring Boot — you don't install it separately. Spring starts it as the last step of its own startup. Every HTTP request after this goes through Tomcat → the matching filter chain → your controllers.

---

This project has two separate layers:

- **MVC / Thymeleaf** — browser-facing HTML pages (categories, cart, checkout, admin panel), protected by form login and session cookies
- **REST API** — JSON endpoints for external clients, protected by JWT tokens

Each chapter below explains one of these layers and walks through exactly how a request travels from the browser through the code to the database and back.

---

---

# Chapter 1 — Security and Authentication

## What are two filter chains?

Spring Security normally has one filter chain that every request passes through. This project has two separate chains because the two parts of the app need completely different security strategies:

- The **HTML side** (browser users) uses username/password in a form → session cookie → JSESSIONID
- The **API side** (REST clients) uses username/password once → JWT token → Authorization header

By splitting into two chains, each part has its own rules and they never interfere with each other.

```
Incoming request
       │
       ▼
Does the URL start with /api/**?
       │
       ├─ YES → Chain 1 (JWT, stateless)
       │         └─ JwtRequestFilter checks Authorization header
       │
       └─ NO  → Chain 2 (Form login, session-based)
                 └─ UsernamePasswordAuthenticationFilter owns POST /login
```

---

## 1.1 How DataInitializer creates the first users (no login needed)

Before anyone can log in, users must already exist in the database. They are not created through the UI — they are seeded automatically at startup by `DataInitializer`.

### What is ApplicationRunner?

`ApplicationRunner` is a Spring Boot interface. If your class implements it and is annotated with `@Component`, Spring calls its `run()` method **immediately after the application context is ready**, before any HTTP request arrives. It runs once every time the app starts.

### Startup seeding call flow

```
App starts (java -jar shop.jar)
  │
  ▼
Spring Boot initializes all beans
  │
  ▼
DataInitializer.run()                          [configuration/DataInitializer.java]
  └─ seedRolesAndUsers()
       └─ RoleRepository.findByName("ROLE_ADMIN")
            └─ SELECT * FROM roles WHERE name = 'ROLE_ADMIN'
            └─ if not found → RoleRepository.save(Role{name="ROLE_ADMIN"})
                 └─ INSERT INTO roles (name) VALUES ('ROLE_ADMIN')
       └─ same for "ROLE_CUSTOMER"
       └─ UserRepository.findByUsername("admin")
            └─ if empty (first run) → user does not exist yet
       └─ UserRepository.save(User{
              username="admin",
              password=BCrypt("password"),   ← plain text never stored
              roles=[ROLE_ADMIN]
          })
            └─ INSERT INTO users (username, email, password) VALUES (...)
       └─ same check + insert for "customer" / ROLE_CUSTOMER
  └─ seedCategoriesAndProducts()
       └─ CategoryRepository.count()
            └─ if > 0 → already seeded, return early
       └─ saves 3 categories: Electronics, Books, Clothing
       └─ saves 4 products (Smartphone, Laptop, Java Programming, T-Shirt)
  │
  ▼
App is now ready to accept HTTP requests
```

**No login is involved here.** This is pure internal Java code that runs inside the server before any client connects. The `passwordEncoder.encode(defaultPassword)` call reads the password from `application.properties` (`app.default-password`) and stores the BCrypt hash — the plain text is never saved.

**Files involved:**
- `DataInitializer.java` — the `ApplicationRunner` that seeds all data
- `Role.java`, `User.java`, `Category.java`, `Product.java` — JPA entities
- `RoleRepository.java`, `UserRepository.java`, `CategoryRepository.java`, `ProductRepository.java` — JPA interfaces, Spring generates the SQL
- `SecurityConfiguration.java` — provides the `PasswordEncoder` (BCrypt) bean injected here

---

## 1.2 Chain 2 — Form Login (browser users)

This handles every request that does NOT match `/api/**` — all the Thymeleaf HTML pages.

### Login call flow (Chain 2)

**Trigger:** User fills in username + password in the browser and clicks Login.

```
Browser
  └─ GET /login
       │
       ▼
Chain 2: /login is permitAll() → no authentication required
       │
       ▼
PublicMvcController.login()                    [controller/mvc/PublicMvcController.java]
  └─ returns view name "login"
  └─ Thymeleaf renders login.html
       └─ <form th:action="@{/login}" method="post">
  └─ Browser displays username + password fields

User fills in form and clicks submit →

Browser
  └─ POST /login  (form body: username=admin&password=password)
       │
       ▼
UsernamePasswordAuthenticationFilter  (Spring built-in, added automatically by .formLogin())
  └─ intercepts POST /login BEFORE it reaches any controller
  └─ no controller method handles POST /login — Spring Security owns that URL
  └─ extracts "username" and "password" from the form body
  └─ creates UsernamePasswordAuthenticationToken("admin", "password")
       └─ this is an unauthenticated token — just a container for the credentials
  └─ passes it to AuthenticationManager
       │
       ▼
AuthenticationManager (ProviderManager, Spring built-in)
  └─ loops through registered providers
  └─ finds DaoAuthenticationProvider
       (registered in SecurityConfiguration via authenticationProvider() bean,
        wired with MyUserDetailsService + BCryptPasswordEncoder)
       │
       ▼
DaoAuthenticationProvider  (Spring built-in)
  └─ calls MyUserDetailsService.loadUserByUsername("admin")
       │
       ▼
MyUserDetailsService.loadUserByUsername()      [service/MyUserDetailsService.java]
  └─ UserRepository.findByUsername("admin")    [repository/UserRepository.java]
       └─ SELECT * FROM users WHERE username = 'admin'
       └─ returns User entity {username, BCryptHash, roles=[ROLE_ADMIN]}
  └─ converts roles → GrantedAuthority list: [ROLE_ADMIN]
  └─ returns Spring UserDetails {username, BCryptHash, [ROLE_ADMIN]}
       │  ← goes back to DaoAuthenticationProvider
       ▼
DaoAuthenticationProvider  (continues)
  └─ BCryptPasswordEncoder.matches("password", "$2a$10$...")
       └─ re-hashes what the user typed and compares to the stored hash
       └─ if no match → throws BadCredentialsException → redirect to /login?error
       └─ if match → builds authenticated token:
            new UsernamePasswordAuthenticationToken(
                userDetails,                   // the UserDetails object
                null,                          // credentials cleared after auth
                [ROLE_ADMIN]                   // authority list
            )
       │
       ▼
Spring Security success handler
  └─ SecurityContextHolder.getContext().setAuthentication(auth)
  └─ HttpSessionSecurityContextRepository saves SecurityContext into HttpSession
  └─ Tomcat sends Set-Cookie: JSESSIONID=ABC123... to browser
  └─ Spring publishes AuthenticationSuccessEvent
       └─ AuthenticationSuccessListener.onApplicationEvent()
            └─ reads username="admin", IP from WebAuthenticationDetails
            └─ LoginHistoryRepository.save(new LoginHistory(...))
                 └─ INSERT INTO login_history (username, ip_address, login_at) VALUES (...)
  └─ redirect 302 → / (defaultSuccessUrl)
```

### What happens on every subsequent page request

After login, the browser automatically sends the `JSESSIONID` cookie on every request:

```
Browser → GET /admin/categories  (includes Cookie: JSESSIONID=ABC123...)
  │
  ▼
Chain 2: SecurityContextPersistenceFilter
  └─ reads JSESSIONID from cookie
  └─ looks up the HttpSession on the server
  └─ restores SecurityContext from session → user is still "admin" with ROLE_ADMIN
  │
  ▼
AuthorizationFilter (rules built from SecurityConfiguration at startup)
  └─ checks: does "admin" have ROLE_ADMIN?  ✓
  └─ if yes → request continues to controller
  └─ if no  → 403 Forbidden, controller never reached
  │
  ▼
AdminMvcController.categories()
```

The user never re-enters credentials because the session on the server still holds the `SecurityContext`. The `JSESSIONID` cookie is just a key to look it up.

### Login failure

```
BCryptPasswordEncoder.matches() → false
  └─ DaoAuthenticationProvider throws BadCredentialsException
  └─ Spring Security AuthenticationFailureHandler
  └─ redirect 302 → /login?error
  └─ Browser → GET /login?error
       └─ PublicMvcController.login() returns "login" view
       └─ Thymeleaf: th:if="${param.error}" → renders "Invalid username or password."
       └─ SecurityContext remains empty, no JSESSIONID set
```

### CSRF token vs JSESSIONID cookie — they are not the same thing

**JSESSIONID cookie** — proves *who you are*. The browser sends it automatically on every request. Spring looks it up on the server to restore your session and know you are logged in.

**CSRF token** — proves the request came *from a page your browser actually loaded from this site*, not from a malicious website tricking your browser into submitting a form.

The attack it prevents:
```
You are logged into shop.com (browser holds JSESSIONID cookie)
You open evil.com in another tab
  └─ evil.com has a hidden form pointing at shop.com/orders/delete
  └─ your browser submits it automatically
  └─ your browser sends JSESSIONID cookie automatically (browsers always do)
  └─ shop.com sees a valid session → executes the delete — you never clicked anything
```

How the CSRF token stops this:
```
When shop.com renders a form it embeds a secret random value:
  <input type="hidden" name="_csrf" value="a3f9...">

Browser → POST /logout  (sends JSESSIONID cookie AND _csrf token in the form body)
  └─ Spring checks: does _csrf match what we issued for this session? ✓ → allowed

evil.com → POST shop.com/orders/delete
  └─ evil.com cannot read the _csrf token from your page (cross-origin policy blocks it)
  └─ Spring checks → no _csrf token → 403 rejected
```

Evil site can *send* your cookie (browser does it automatically) but it can never *read* the CSRF token from a page it did not serve — so it cannot forge a valid form submission.

**CSRF only applies to state-changing requests.** GET requests are exempt because they are supposed to be read-only — a GET cannot delete or modify data, so there is nothing dangerous to forge. Spring's `CsrfFilter` only checks the token on `POST`, `PUT`, `PATCH`, and `DELETE`.

```
GET  /categories     → CsrfFilter skips it entirely
GET  /admin/orders   → CsrfFilter skips it entirely
POST /logout         → CsrfFilter checks _csrf token ← required
POST /cart/add       → CsrfFilter checks _csrf token ← required
POST /checkout       → CsrfFilter checks _csrf token ← required
```

| | JSESSIONID cookie | CSRF token |
|---|---|---|
| **What it proves** | Who you are | The form came from a page this site served |
| **Where it lives** | Cookie (sent automatically by browser) | Hidden field in every HTML form |
| **Who checks it** | `SecurityContextPersistenceFilter` | Spring's `CsrfFilter` |
| **Which methods** | All requests | POST, PUT, PATCH, DELETE only |
| **Protects against** | Needing to re-authenticate | Cross-site request forgery attacks |

---

### Logout

```
Browser → POST /logout  (CSRF token included in the form)
  └─ LogoutFilter intercepts /logout
  └─ SecurityContextHolder.clearContext()
  └─ HttpSession.invalidate() — session destroyed on server
  └─ JSESSIONID cookie cleared in browser (Max-Age=0)
  └─ redirect 302 → /login?logout
  └─ Thymeleaf: th:if="${param.logout}" → renders "You have been logged out."
```

**Files involved:**
- `PublicMvcController.java` — serves the GET /login page
- `SecurityConfiguration.java` — `.formLogin()` registers `UsernamePasswordAuthenticationFilter`, `.logout()` registers `LogoutFilter`
- `MyUserDetailsService.java` — your code called by Spring to load user from DB
- `UserRepository.java` — JPA interface, Spring generates the SQL
- `DaoAuthenticationProvider` — Spring built-in, calls your service, runs BCrypt
- `AuthenticationSuccessListener.java` — records the login event
- `LoginHistoryRepository.java` — saves the `LoginHistory` record

---

## 1.3 Chain 1 — JWT / REST (API clients)

This handles every request under `/api/**`. It is completely stateless — no session, no cookie.

### JWT login call flow (Chain 1)

**Trigger:** API client sends username + password as JSON.

```
POST /api/auth/login
Content-Type: application/json
{"username": "admin", "password": "password"}
  │
  ▼
Chain 1: /api/auth/** is permitAll() → reaches the controller without authentication
  │
  ▼
JwtRequestFilter.doFilterInternal()            [filter/JwtRequestFilter.java]
  └─ reads Authorization header → not present on a login request
  └─ does nothing, calls filterChain.doFilter() → passes through to controller
  │
  ▼
AuthRestController.login()                     [controller/rest/AuthRestController.java]
  └─ authenticationManager.authenticate(
         new UsernamePasswordAuthenticationToken("admin", "password")
     )
       └─ this is the CONTROLLER calling AuthenticationManager directly in Java code
       └─ same DaoAuthenticationProvider and MyUserDetailsService as Chain 2
       └─ BCrypt hash check → success or BadCredentialsException → 401
  └─ jwtUtil.generateToken(auth.getName())     [configuration/JwtUtil.java]
       └─ builds JWT: subject="admin", issued-at=now, expiry=now+24h
       └─ signs it with HMAC-SHA256 using the secret key from application.properties
       └─ returns token string "eyJ..."
  └─ returns HTTP 200 {"token": "eyJhbGci..."}
```

The client stores this token and sends it in every future request header.

---

### Per-request JWT validation (Chain 1)

Every call to a protected endpoint after login:

```
GET /api/products
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
  │
  ▼
JwtRequestFilter.doFilterInternal()
  └─ reads Authorization header → "Bearer eyJ..."
  └─ strips "Bearer " prefix → token = "eyJ..."
  └─ jwtUtil.isTokenValid(token)
       └─ verifies HMAC-SHA256 signature (detects tampering)
       └─ checks expiry date
       └─ if invalid/expired → does nothing → request continues unauthenticated → 401 later
  └─ jwtUtil.extractUsername(token) → "admin"
  └─ myUserDetailsService.loadUserByUsername("admin")
       └─ UserRepository.findByUsername("admin")
            └─ SELECT * FROM users WHERE username = 'admin'
            └─ returns user with roles
  └─ builds:
       new UsernamePasswordAuthenticationToken(userDetails, null, [ROLE_ADMIN])
       └─ null credential = already authenticated, no password check needed
  └─ SecurityContextHolder.getContext().setAuthentication(auth)
       └─ SecurityContext is populated for this request thread only
  └─ filterChain.doFilter() → continues to controller
  │
  ▼
AuthorizationFilter
  └─ reads SecurityContext → ROLE_ADMIN present
  └─ /api/products is permitAll() → allowed without any role
  │
  ▼
ProductRestController.getAll()                 [controller/rest/ProductRestController.java]
  └─ ProductService.getAllProducts()
       └─ ProductRepository.findAll()
            └─ SELECT * FROM products
  └─ converts each Product → ProductDTO
  └─ returns JSON array
```

**Important:** The SecurityContext is populated fresh on every single request from the token alone. No session exists — the token carries everything.

**Files involved:**
- `JwtRequestFilter.java` — reads and validates the token, populates SecurityContext
- `JwtUtil.java` — generates tokens and validates them (signature + expiry)
- `AuthRestController.java` — the login endpoint that issues tokens
- `MyUserDetailsService.java` — reused from Chain 2, loads user from DB
- `LoginRequest.java` — DTO for `{username, password}` request body

---

### What happens before every request reaches a controller

Every request passes through a chain of filters in order before the controller is ever touched. The `AuthorizationFilter` is always last — it reads whatever the earlier filters put into `SecurityContextHolder` and decides allow or block.

**Chain 2 — browser request to a protected page:**

```
Browser → GET /admin/categories  (Cookie: JSESSIONID=ABC123)
  │
  ▼
① SecurityContextPersistenceFilter
     └─ reads JSESSIONID cookie → looks up session on server
     └─ restores SecurityContext → user="admin", roles=[ROLE_ADMIN]
     └─ puts it in SecurityContextHolder for this request thread
  │
  ▼
② CsrfFilter
     └─ GET request → skipped entirely
  │
  ▼
③ AuthorizationFilter
     └─ reads SecurityContext from SecurityContextHolder
     └─ checks rule: .requestMatchers("/admin/**").hasRole("ADMIN")
     └─ user has ROLE_ADMIN ✓ → allowed
     └─ if no match → 403, controller never reached
  │
  ▼
DispatcherServlet → AdminMvcController.categories()
```

Same flow but for a POST (e.g. adding to cart):

```
Browser → POST /cart/add  (Cookie: JSESSIONID=ABC123, body includes _csrf token)
  │
  ▼
① SecurityContextPersistenceFilter  → restores SecurityContext from session
  │
  ▼
② CsrfFilter
     └─ POST request → CSRF check runs
     └─ reads _csrf from request body
     └─ compares to token stored in session
     └─ if mismatch → 403, nothing further runs
  │
  ▼
③ AuthorizationFilter
     └─ /cart/add is permitAll() → no role needed ✓
  │
  ▼
DispatcherServlet → CartMvcController.addToCart()
```

**Chain 1 — API request with JWT:**

```
Browser → GET /api/products  (Authorization: Bearer eyJ...)
  │
  ▼
① JwtRequestFilter
     └─ validates token, extracts username, populates SecurityContextHolder
  │
  ▼
② CsrfFilter
     └─ Chain 1 has csrf disabled (.csrf(csrf -> csrf.disable()))
     └─ skipped entirely — REST APIs use tokens, not cookies, so CSRF is not a threat
  │
  ▼
③ AuthorizationFilter
     └─ /api/products is permitAll() → allowed ✓
  │
  ▼
DispatcherServlet → ProductRestController.getAll()
```

If `SecurityContextHolder` is empty at step ③ (no valid session, no valid token), `AuthorizationFilter` sees an anonymous user and blocks anything that is not `permitAll()` with a 401 or 403.

---

### The key difference between the two chains

| | Chain 2 — Form Login | Chain 1 — JWT |
|---|---|---|
| What triggers authentication | `UsernamePasswordAuthenticationFilter` intercepts POST /login automatically | Controller calls `AuthenticationManager` manually in Java code |
| What you get back | JSESSIONID cookie | JWT token string in JSON |
| How future requests prove identity | Browser sends cookie automatically | Client sends `Authorization: Bearer ...` header manually |
| Server memory between requests | Session stored on server | Nothing — token carries everything |

---

---

# Chapter 2 — MVC Web Layer (Thymeleaf)

## What is Thymeleaf?

Thymeleaf is a server-side template engine. Your controller builds a `Model` object (a map of data), then tells Spring which HTML template to render. Thymeleaf merges the template with the data and sends the final HTML to the browser. The browser receives plain HTML — no JavaScript framework involved.

Every flow in this chapter goes through Chain 2. The `SecurityContextPersistenceFilter` restores the session on each request, so the user is already authenticated when the controller runs. It is not repeated in each flow.

---

## 2.1 Browsing categories and products

```
Browser → GET /categories
  │
  ▼
Chain 2: /categories is permitAll() → no login required
  │
  ▼
PublicMvcController.categories()               [controller/mvc/PublicMvcController.java]
  └─ CategoryService.getAllCategories()        [service/impl/CategoryServiceImpl.java]
       └─ CategoryRepository.findAll()         [repository/CategoryRepository.java]
            └─ SELECT * FROM categories
            └─ returns List<Category>
  └─ model.addAttribute("categories", list)
  └─ model.addAttribute("cartCount", cartService.getTotalItemsCount())
  └─ returns view name "categories"
  │
  ▼
ThymeleafViewResolver resolves "categories" → classpath:/templates/categories.html
  └─ Thymeleaf renders HTML with the category list
  └─ Browser displays the page
```

Clicking a category:

```
Browser → GET /categories/1/products
  │
  ▼
PublicMvcController.productsByCategory(id=1)
  └─ CategoryService.getCategoryById(1)
       └─ CategoryRepository.findById(1) → Category{name="Electronics"}
  └─ ProductService.getProductsByCategory(1)
       └─ ProductRepository.findByCategoryId(1)
            └─ SELECT * FROM products WHERE category_id = 1
  └─ model.addAttribute("category", electronics)
  └─ model.addAttribute("products", list)
  └─ returns view "products"
  └─ Thymeleaf renders products.html
```

---

## 2.2 Cart (session-scoped)

### What does @SessionScope mean?

`CartService` is annotated with `@SessionScope`. This means Spring creates a **separate instance of CartService for each HTTP session** — one per browser tab's session. When you add items in one browser, another browser's cart is unaffected.

The cart is a `Map<Long, Integer>` (productId → quantity) stored in that session-scoped bean. It lives until the session ends (logout or browser close).

```
Browser → POST /cart/add?productId=1&quantity=2
  │
  ▼
Chain 2: /cart/** is permitAll() → anonymous users can add to cart
  │
  ▼
CartMvcController.addToCart(productId=1, quantity=2)   [controller/mvc/CartMvcController.java]
  └─ CartService.addItem(1, 2)                          [service/CartService.java]
       └─ items.put(1, items.getOrDefault(1, 0) + 2)
            └─ if product 1 already in cart → adds to existing quantity
            └─ if new → puts {1: 2} in the map
  └─ redirect to /cart

Browser → GET /cart
  │
  ▼
CartMvcController.viewCart()
  └─ CartService.getItems() → Map{1: 2}
  └─ for each entry:
       └─ ProductService.getProductById(1)
            └─ ProductRepository.findById(1) → Product{name="Smartphone", price=699.99}
       └─ builds CartItemDTO {product, quantity=2, subtotal=1399.98}
  └─ calculates total
  └─ model.addAttribute("cartItems", list)
  └─ model.addAttribute("total", 1399.98)
  └─ returns view "cart"
```

---

## 2.3 Checkout — Cash payment

```
Browser → GET /checkout
  │
  ▼
Chain 2: /checkout/** requires ROLE_CUSTOMER → must be logged in as customer
  │
  ▼
CheckoutMvcController.showCheckout()           [controller/mvc/CheckoutMvcController.java]
  └─ buildCartProducts()
       └─ CartService.getItems() → Map{1: 2, 3: 1}
       └─ for each productId → ProductService.getProductById() → Product entity
       └─ returns Map<Product, Integer>
  └─ calculateTotal() → sums price × quantity for all items
  └─ model.addAttribute("cartProducts", map)
  └─ model.addAttribute("total", total)
  └─ returns view "checkout"

User selects "Cash" and submits →

Browser → POST /checkout?paymentMethod=CASH
  │
  ▼
CheckoutMvcController.placeOrder(paymentMethod="CASH", auth)
  └─ paymentMethod is not "PAYPAL" → goes to cash path
  └─ buildAndSaveOrder(auth.getName(), "CASH")
       └─ UserRepository.findByUsername("customer") → User entity
       └─ builds Order {user, createdAt=now, paymentMethod="CASH", items=[]}
       └─ for each cart item:
            └─ ProductService.getProductById(id) → Product
            └─ builds OrderItem {order, product, quantity, priceAtPurchase=product.price}
            └─ adds to items list, accumulates total
       └─ order.setTotalAmount(total)
       └─ OrderService.createOrder(order)   [service/impl/OrderServiceImpl.java]
            └─ OrderRepository.save(order)
                 └─ INSERT INTO orders (user_id, created_at, payment_method, total_amount)
                 └─ INSERT INTO order_items (order_id, product_id, quantity, price_at_purchase) × N
       └─ CartService.clear() → empties the session cart
       └─ returns saved Order
  └─ model.addAttribute("order", saved)
  └─ returns view "order-confirmation"
```

---

## 2.4 Checkout — PayPal payment

PayPal requires two separate HTTP round-trips to their API: one to create the order and get an approval URL, and one to capture (finalise) the payment after the user approves.

```
Browser → POST /checkout?paymentMethod=PAYPAL
  │
  ▼
CheckoutMvcController.placeOrder(paymentMethod="PAYPAL", auth)
  └─ calculateTotal(buildCartProducts()) → e.g. 1399.98 EUR
  └─ PayPalService.createOrder(1399.98)  [service/PayPalService.java]

       Step A — get a short-lived bearer token from PayPal:
       └─ Base64.encode(clientId + ":" + clientSecret)
       └─ RestClient POST https://api-m.sandbox.paypal.com/v1/oauth2/token
            Header: Authorization: Basic <base64>
            Body: grant_type=client_credentials
       └─ PayPal returns {"access_token": "A21AA..."}

       Step B — create a PayPal order:
       └─ RestClient POST https://api-m.sandbox.paypal.com/v2/checkout/orders
            Header: Authorization: Bearer A21AA...
            Body: {intent:"CAPTURE", purchase_units:[{amount:{currency_code:"EUR",value:"1399.98"}}],
                   application_context:{return_url:"https://.../checkout/paypal/return",
                                        cancel_url:"https://.../checkout/paypal/cancel"}}
       └─ PayPal returns order with links: [{rel:"approve", href:"https://paypal.com/checkoutnow?token=XYZ"}]
       └─ extracts the "approve" link href
       └─ returns the URL

  └─ return "redirect:" + approvalUrl
  └─ Browser redirected to PayPal's website

User logs into PayPal sandbox and approves the payment →
PayPal redirects browser back to:

Browser → GET /checkout/paypal/return?token=XYZ
  │
  ▼
CheckoutMvcController.paypalReturn(token="XYZ", auth)
  └─ PayPalService.captureOrder("XYZ")
       └─ getAccessToken() → new PayPal bearer token (same as Step A above)
       └─ RestClient POST https://api-m.sandbox.paypal.com/v2/checkout/orders/XYZ/capture
            Header: Authorization: Bearer A21AA...
       └─ PayPal finalises the payment, returns capture details
  └─ buildAndSaveOrder(auth.getName(), "PAYPAL")
       └─ (same as cash flow above — saves order + items to H2, clears cart)
  └─ model.addAttribute("order", saved)
  └─ returns view "order-confirmation"
```

If the user cancels on PayPal:

```
Browser → GET /checkout/paypal/cancel
  └─ return "redirect:/checkout"  — user goes back to checkout page, cart unchanged
```

**Files involved:**
- `CheckoutMvcController.java` — orchestrates both cash and PayPal flows
- `PayPalService.java` — all communication with PayPal REST API
- `OrderService.java` / `OrderServiceImpl.java` — saves the order
- `CartService.java` — cleared after a successful order
- `OrderRepository.java`, `OrderItemRepository.java` — JPA interfaces

---

## 2.5 Admin panel

All admin endpoints require `ROLE_ADMIN`. If a non-admin accesses them, the `AuthorizationFilter` returns 403 before the controller runs.

### Category CRUD

```
Browser → GET /admin/categories
  │
  ▼
AdminMvcController.categories()               [controller/mvc/AdminMvcController.java]
  └─ CategoryService.getAllCategories()
       └─ CategoryRepository.findAll() → List<Category>
  └─ model.addAttribute("categories", list)
  └─ returns view "admin/categories"

Browser → POST /admin/categories/save
  Body: name=Furniture&description=Home+furniture
  │
  ▼
AdminMvcController.saveCategory(@ModelAttribute CategoryForm form)
  └─ Category entity built from CategoryForm DTO
       └─ CategoryForm is separate from the JPA entity — avoids mass assignment risk
  └─ CategoryService.save(category)
       └─ CategoryRepository.save(category)
            └─ INSERT or UPDATE (JPA decides based on whether id is set)
  └─ redirect to /admin/categories
```

### Product CRUD — add new product

**Step 1 — Admin clicks "New Product" (load the empty form):**

```
Browser → GET /admin/products/new
  │
  ▼
Chain 2: requires ROLE_ADMIN ✓
  │
  ▼
AdminMvcController.newProductForm()
  └─ model.addAttribute("product", new ProductForm())
       └─ empty ProductForm so Thymeleaf has an object to bind the form fields to
  └─ model.addAttribute("categories", categoryService.getAllCategories())
       └─ CategoryRepository.findAll() → List<Category>
       └─ needed to populate the <select> category dropdown in the form
  └─ returns PRODUCTS_FORM_VIEW → "admin/products/form"
  └─ Thymeleaf renders the empty form with the category dropdown filled
```

**Step 2 — Admin fills in the form and clicks Save:**

```
Browser → POST /admin/products/save
  Body: name=Headphones&description=Wireless&price=99.99&stockQuantity=30&categoryId=1
  │
  ▼
AdminMvcController.saveProduct(@Valid @ModelAttribute ProductForm form, BindingResult result)
  └─ Spring binds each form field onto a ProductForm object:
       form.name          = "Headphones"
       form.description   = "Wireless"
       form.price         = 99.99
       form.stockQuantity = 30
       form.categoryId    = 1      ← just a Long, not a Category object
  └─ @Valid runs bean validation on the form fields (e.g. @NotBlank, @NotNull)
  └─ if result.hasErrors():
       └─ model.addAttribute("categories", ...) — must re-add, model is lost on POST
       └─ returns PRODUCTS_FORM_VIEW — re-renders form with validation error messages
  └─ if valid:
       └─ CategoryService.getCategoryById(1)
            └─ CategoryRepository.findById(1) → Category{name="Electronics"}
            └─ needed because Product entity requires a full Category object, not just an id
       └─ Product.builder()
            .name("Headphones")
            .description("Wireless")
            .price(99.99)
            .stockQuantity(30)
            .category(category)    ← full Category entity
            .build()
       └─ ProductService.save(product)
            └─ ProductRepository.save(product)
                 └─ INSERT INTO products (name, description, price, stock_quantity, category_id)
       └─ attrs.addFlashAttribute("success", "Product saved successfully.")
            └─ flash attribute survives the redirect and is shown once on the next page
       └─ return REDIRECT_PRODUCTS → "redirect:/admin/products"
  │
  ▼
Browser → GET /admin/products  (after redirect)
  └─ AdminMvcController.listProducts() — shows all products including the new one
  └─ green success message displayed from the flash attribute
```

**Why `ProductForm` instead of `Product` directly?** An HTML `<select>` submits a `Long` id, not a full `Category` object. `ProductForm` has a `categoryId` field to receive that. The controller then does a separate DB lookup to get the full `Category` entity before building the `Product`. This also protects against mass assignment — the form can only set the fields `ProductForm` exposes.

**Edit** follows the same POST flow but `GET /admin/products/{id}/edit` pre-fills the form by loading the existing product and copying its fields into a `ProductForm`. The `id` field is included so `ProductRepository.save()` issues an `UPDATE` instead of an `INSERT` (JPA checks: if id is set → update, if null → insert).

**Delete:**

```
Browser → POST /admin/products/{id}/delete
  │
  ▼
AdminMvcController.deleteProduct(id)
  └─ ProductService.deleteById(id)
       └─ ProductRepository.deleteById(id)
            └─ DELETE FROM products WHERE id = ?
  └─ flash attribute "Product deleted."
  └─ return REDIRECT_PRODUCTS
```

**Files involved:**
- `AdminMvcController.java` — all admin endpoints
- `ProductForm.java` — DTO decoupling the HTML form from the JPA entity
- `ProductService.java` / `ProductServiceImpl.java` — save and delete logic
- `ProductRepository.java` — JPA interface, Spring generates SQL
- `CategoryService.java` — fetches the full Category entity by id

### Login history view

```
Browser → GET /admin/login-history
  │
  ▼
AdminMvcController.loginHistory()
  └─ LoginHistoryRepository.findAll()
       └─ SELECT * FROM login_history ORDER BY login_at DESC
  └─ model.addAttribute("logins", list)
  └─ returns view "admin/login-history"
  └─ table shows: username, IP address, time of login
```

### Orders search

```
Browser → GET /admin/orders?username=cust&from=2026-01-01&to=2026-12-31
  │
  ▼
AdminMvcController.orders(username, from, to)
  └─ OrderService.searchOrders(username, from, to)
       └─ builds JPA Specification dynamically:
            └─ if username not blank → OrderSpecification.hasUsername("cust")
                 → WHERE LOWER(user.username) LIKE '%cust%'
            └─ if from date present → OrderSpecification.createdAfter(from)
                 → WHERE created_at >= 2026-01-01 00:00:00
            └─ if to date present → OrderSpecification.createdBefore(to)
                 → WHERE created_at <= 2026-12-31 23:59:59
       └─ OrderRepository.findAll(spec)
            └─ Hibernate composes the WHERE clause from the specs
            └─ returns only orders matching all filters
  └─ model.addAttribute("orders", list)
  └─ returns view "admin/orders"
```

---

---

# Chapter 3 — REST API and AJAX Product Search

## What is AJAX?

AJAX (Asynchronous JavaScript and XML) means the browser can fetch data from the server in the background **without reloading the page**. JavaScript sends a `fetch()` request, gets JSON back, and updates the DOM directly. The user sees results appear while typing — no full page reload.

---

## 3.1 AJAX product search

When a user browses a category page (`/categories/{id}/products`) and types in the search box, JavaScript calls the REST API:

```
User types "smart" in the search box
  │
  ▼
JavaScript (products.html, runs in browser)
  └─ fetch("/api/products/search?name=smart&categoryId=1")
       └─ HTTP GET — no page reload
       │
       ▼
Chain 1 (JWT): /api/products/** is permitAll() → no token required for search
       │
       ▼
ProductRestController.search(name="smart", categoryId=1)   [controller/rest/ProductRestController.java]
  └─ ProductService.searchByNameInCategory("smart", 1)     [service/impl/ProductServiceImpl.java]
       └─ ProductRepository.findByNameStartingWithIgnoreCaseAndCategoryId("smart", 1)
            └─ SELECT * FROM products
               WHERE LOWER(name) LIKE 'smart%'
               AND category_id = 1
  └─ converts each Product → ProductDTO
  └─ returns JSON: [{id:1, name:"Smartphone", price:699.99, ...}]
       │
       ▼
JavaScript receives JSON
  └─ clears the current product cards on the page
  └─ renders new product cards from the JSON data
  └─ user sees "Smartphone" appear instantly while still typing
```

**Files involved:**
- `ProductRestController.java` — REST endpoints returning JSON
- `ProductServiceImpl.java` — delegates to repository
- `ProductRepository.java` — custom query method, Spring generates the SQL from the method name
- `ProductDTO.java` — the JSON shape returned to the client (no JPA annotations)
- `products.html` — contains the JavaScript `fetch()` call

---

## 3.2 Full product list and single product (REST)

> These endpoints are also in Chain 1 and `permitAll()` — no token required.

```
GET /api/products
  │
  ▼
ProductRestController.getAll()
  └─ ProductService.getAllProducts()
       └─ ProductRepository.findAll() → List<Product>
  └─ maps each to ProductDTO
  └─ returns JSON array

GET /api/products/3
  │
  ▼
ProductRestController.getById(id=3)
  └─ ProductService.getProductById(3)
       └─ ProductRepository.findById(3) → Product
  └─ returns single ProductDTO as JSON
```

---

---

# Chapter 4 — Login History (ApplicationListener)

## What is ApplicationListener?

`ApplicationListener<T>` is a Spring interface that lets your code react to events that Spring publishes internally. You never call it directly — Spring calls it automatically when the event fires.

`AuthenticationSuccessListener` is annotated with `@Component` and implements `ApplicationListener<AuthenticationSuccessEvent>`. Spring publishes `AuthenticationSuccessEvent` automatically every time any authentication succeeds — both Chain 2 (form login) and Chain 1 (JWT login through `AuthenticationManager`).

### Call flow

```
Any successful login (Chain 1 or Chain 2)
  │
  ▼
Spring Security publishes AuthenticationSuccessEvent

AuthenticationSuccessListener.onApplicationEvent(event)  [listener/AuthenticationSuccessListener.java]
  └─ event.getAuthentication().getName() → "admin"
  └─ event.getAuthentication().getDetails() instanceof WebAuthenticationDetails
       └─ details.getRemoteAddress() → "x.x.x.x"
       └─ if not WebAuthenticationDetails (e.g. programmatic call) → ip = "unknown"
  └─ LoginHistoryRepository.save(LoginHistory{
         username = "admin",
         ipAddress = "x.x.x.x",
         loginAt = LocalDateTime.now()
     })
       └─ INSERT INTO login_history (username, ip_address, login_at) VALUES (...)
```

This runs **in the same request thread** as the login — it is synchronous, not a background task. The redirect to `/` only happens after this completes.

**Files involved:**
- `AuthenticationSuccessListener.java` — listens for the event, saves the record
- `LoginHistory.java` — JPA entity, maps to `login_history` table
- `LoginHistoryRepository.java` — JPA interface, Spring generates the SQL

---

---

# Summary — How the two layers compare

| | MVC / Thymeleaf | REST / JWT |
|---|---|---|
| Who uses it | Browser (HTML forms) | API clients (curl, mobile, JS fetch) |
| Returns | HTML pages | JSON |
| Authentication | Form login → JSESSIONID cookie | JSON login → JWT token |
| State | Session stored on server | Stateless — token carries identity |
| Filter chain | Chain 2 (Order 2) | Chain 1 (Order 1) |
| Public endpoints | `/`, `/categories/**`, `/products/**`, `/cart/**`, `/login` | `/api/auth/**`, `/api/products/**` |
| Protected (customer) | `/checkout/**`, `/orders/**` | — |
| Protected (admin) | `/admin/**` | — |
| Async mechanism | JavaScript fetch() calls the REST API for live search | ProductRestController returns JSON |