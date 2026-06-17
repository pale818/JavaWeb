now# 3-Day Exam Plan — Java Web Online Shop

## Overview

**Stack:** Spring Boot 3.4.x · Spring MVC · Thymeleaf · Spring Data JPA · Spring Security · JWT · H2 (dev) / PostgreSQL (prod) · Bootstrap 5 · Lombok

**Mandatory checklist (exam blockers):**
- [ ] Hosted on external server before presentation
- [ ] SonarQube scan with ALL issues resolved
- [ ] Security filter (at least one)
- [ ] Listener (at least one)
- [ ] JWT tokens on REST API
- [ ] PayPal sandbox integration
- [ ] Async mechanism (AJAX)

---

## Day 1 — Project Foundation + Anonymous Features + Cart

**Goal:** Runnable app with sample data, public pages, and a working session cart.

### 1. Spring Boot Project Setup (~1h)
- Create project via Spring Initializr with dependencies:
  - Spring Web, Spring Data JPA, Spring Security, Thymeleaf, Validation, Lombok, H2, DevTools
  - Add manually: `jjwt` (JWT), `thymeleaf-extras-springsecurity6`
- Configure `application.properties`: server port, H2 console, JPA settings
- Mirror the package structure from CarsApp-10: `model`, `dto`, `repository`, `service`, `controller/mvc`, `controller/rest`, `configuration`, `filter`, `listener`, `specification`

### 2. Database Schema + JPA Entities (~2h)
Design and implement these entities (follow CarsApp-10's `Car` / `User` / `Role` pattern):

| Entity | Key Fields |
|---|---|
| `Category` | id, name, description |
| `Product` | id, name, description, price (BigDecimal), stockQuantity, category (ManyToOne) |
| `User` | id, username, email, password, roles (ManyToMany) |
| `Role` | id, name (`CUSTOMER`, `ADMIN`) |
| `Order` | id, user (ManyToOne), createdAt, paymentMethod (`CASH`/`PAYPAL`), totalAmount |
| `OrderItem` | id, order (ManyToOne), product (ManyToOne), quantity, priceAtPurchase |
| `LoginHistory` | id, username, ipAddress, loginAt |

- Write `schema-backup.sql` and `data.sql` (sample categories, products, users with BCrypt passwords — copy the pattern from CarsApp-10's `data.sql`)
- Create Spring Data JPA repositories for each entity

### 3. Services (~1h)
- `CategoryService` / `CategoryServiceImpl`
- `ProductService` / `ProductServiceImpl`
- `CartService` — session-scoped (`@SessionScope`) bean holding `Map<Long, Integer>` (productId → quantity); methods: `addItem`, `updateQuantity`, `removeItem`, `clear`, `getItems`, `getTotal`

### 4. Anonymous Public Pages — MVC Controllers + Thymeleaf (~2h)
- `PublicMvcController` → `/` home, `/categories`, `/categories/{id}/products`, `/products/{id}`
- Thymeleaf templates using Bootstrap 5 (CDN): `home.html`, `categories.html`, `products.html`, `product-detail.html`
- Add navbar with cart icon showing item count (pulled from session)

### 5. Session Cart (~1.5h)
- `CartMvcController` → `/cart` (view), `/cart/add`, `/cart/update`, `/cart/remove`, `/cart/clear`
- `cart.html` template: table of items with quantity inputs, remove buttons, total price, "Proceed to Checkout" button (redirects to login if anonymous)

### 6. Basic Security Config — Permit Anonymous Routes (~0.5h)
- `SecurityConfiguration.java`: permit all to `/`, `/categories/**`, `/products/**`, `/cart/**`
- Block `/checkout/**`, `/orders/**`, `/admin/**`
- Set up login page route but keep the full implementation for Day 2

**End of Day 1:** App runs, you can browse categories/products and manage the cart without logging in.

---

## Day 2 — Authentication + Admin CRUD + Customer Checkout + REST API

**Goal:** Full auth flow, all admin features, customer order flow, JWT-secured REST, async AJAX.

### 1. Spring Security — Full Setup (~1.5h)
- `MyUserDetailsService` (same pattern as CarsApp-10): load user from DB, map roles to `GrantedAuthority`
- `SecurityConfiguration`: finalize role-based rules, configure login/logout pages, BCrypt password encoder
- `login.html` template with error/logout message support

### 2. Session Listener — Login History Tracking (~0.5h)
- `AuthenticationSuccessListener` implementing `ApplicationListener<AuthenticationSuccessEvent>`:
  - On successful login: save `LoginHistory` record (username, timestamp, IP from `WebAuthenticationDetails`)
- This satisfies the **listener (LO6)** requirement

### 3. Request Logging Filter (~0.5h)
- `RequestResponseLoggingFilter` (copy/adapt from CarsApp-10): log method, URI, response status
- This satisfies the **security filter (LO5)** requirement

### 4. Customer Checkout + Order Persistence (~2h)
- `CheckoutMvcController` → `GET /checkout` (show summary), `POST /checkout` (place order)
- `checkout.html`: cart summary, payment method radio (Cash on Delivery / PayPal), confirm button
- On `POST`: create `Order` + `OrderItems`, persist to DB, clear cart, redirect to confirmation
- `order-confirmation.html`: success message with order details
- Wire PayPal button — for now show it disabled (implement PayPal on Day 3)

### 5. Customer Purchase History (~0.5h)
- `CustomerMvcController` → `GET /orders/my`
- `my-orders.html`: table of past orders (date, total, payment method) with expandable item details

### 6. Admin — Category & Product CRUD (~2h)
- `AdminMvcController` → `/admin/categories/**`, `/admin/products/**`
- Templates: `admin/categories/list.html`, `admin/categories/form.html` (create/edit), same for products
- Validation with `@Valid` + `BindingResult` (follow CarsApp-10's `newCar.html` pattern)
- Delete with confirmation

### 7. Admin — Login History + All Orders Overview (~1h)
- `GET /admin/login-history` → paginated table (username, IP, timestamp)
- `GET /admin/orders` → all orders, search filter form by customer (username) and date range
  - Use JPA Specifications (follow CarsApp-10's `CarSpecification`) for dynamic filtering
- Templates: `admin/login-history.html`, `admin/orders.html`

### 8. REST API + JWT (~2h)
- `AuthRestController` → `POST /api/auth/login`: accepts username/password, returns JWT token
- `ProductRestController` → `GET /api/products`, `GET /api/products/{id}`, `GET /api/products/search?name=`
- `JwtRequestFilter` (OncePerRequestFilter): validates Bearer token from `Authorization` header
- `JwtUtil`: generate + validate tokens using `jjwt`
- Secure `/api/**` routes via `SecurityConfiguration` (JWT stateless chain)

### 9. Async Mechanism — AJAX Product Search (~0.5h)
- In `products.html`: add a search input with `onkeyup` → fetch `/api/products/search?name=` (≥3 chars) → render results dynamically (follow CarsApp-10's `searchCars.html` AJAX pattern)
- This satisfies the **async mechanism** requirement

**End of Day 2:** Fully functional app — auth, admin, customer orders, JWT REST, AJAX search.

---

## Day 3 — PayPal + UI Polish + SonarQube + Deployment

**Goal:** PayPal integrated, UI clean, SonarQube clean, app live on hosting.

### 1. PayPal Sandbox Integration (~2h)
- Create sandbox accounts at https://developer.paypal.com/ (business + personal)
- Add PayPal JS SDK script to `checkout.html`
- Render PayPal button only when "PayPal" payment method is selected
- On PayPal approval callback: call `POST /checkout/paypal/capture` → persist order with `paymentMethod=PAYPAL`
- Test full PayPal flow with sandbox credentials

### 2. Bootstrap UI Polish (~1.5h)
- Consistent navbar on all pages (active link highlighting, cart badge, login/logout button)
- Responsive product grid (Bootstrap cards) on `products.html`
- Admin sidebar layout for admin pages
- Form validation styles (Bootstrap `is-invalid` / `is-valid` classes tied to Thymeleaf errors)
- Flash messages for success/error operations (use session attributes + Thymeleaf fragments)

### 3. SonarQube Scan + Fix (~2h)
- Run SonarQube locally (Docker: `docker run -d -p 9000:9000 sonarqube`) or use SonarCloud (free)
- Add SonarQube Maven plugin to `pom.xml`, run `mvn sonar:sonar`
- Fix ALL reported issues — common ones to expect:
  - Hardcoded credentials → move to `application.properties`
  - Missing `@Override` annotations
  - Unused imports / variables
  - SQL injection risks in custom queries → use parameterized queries
  - Missing null checks
- Re-scan until zero issues

### 4. Switch to PostgreSQL for Production (~0.5h)
- Add `postgresql` driver to `pom.xml`
- Create `application-prod.properties` with PostgreSQL connection string
- Ensure `schema-backup.sql` + `data.sql` are PostgreSQL-compatible (no H2-specific syntax)

### 5. Deploy to Hosting (~1.5h)
- **Recommended: Railway.app** (free tier, supports Spring Boot + PostgreSQL easily)
  - `railway init` → add PostgreSQL plugin → set env vars (`SPRING_PROFILES_ACTIVE=prod`, DB connection vars)
  - `railway up` to deploy JAR
- Alternative: Render.com (also free, similar process)
- Verify all features work on the live URL

### 6. Final End-to-End Testing (~1h)
Test every user story from the spec on the live URL:
- [ ] Anonymous: browse categories → view products → add to cart → change quantities → remove items
- [ ] Anonymous → login → checkout (cash on delivery) → see order in history
- [ ] Anonymous → login → checkout (PayPal sandbox) → see order in history
- [ ] Admin login → add/edit/delete category → add/edit/delete product
- [ ] Admin → view login history (should show your test logins with IPs)
- [ ] Admin → view all orders → filter by customer name → filter by date range
- [ ] REST API: get JWT token → use it to call `/api/products` → verify 401 without token
- [ ] AJAX search: type 3+ chars in product search → results appear without page reload

---

## Key File Reference (what to build, in order)

```
src/main/java/.../
├── model/                  Category, Product, User, Role, Order, OrderItem, LoginHistory
├── repository/             One JPA repo per entity + Specifications for filtering
├── service/                CategoryService, ProductService, CartService (session), OrderService, UserDetailsService
├── dto/                    ProductDTO, OrderDTO (for REST responses)
├── controller/
│   ├── mvc/                PublicMvcController, CartMvcController, CheckoutMvcController,
│   │                       CustomerMvcController, AdminMvcController
│   └── rest/               AuthRestController, ProductRestController
├── configuration/          SecurityConfiguration (MVC chain + JWT chain), JwtUtil
├── filter/                 RequestResponseLoggingFilter, JwtRequestFilter
├── listener/               AuthenticationSuccessListener
└── specification/          ProductSpecification, OrderSpecification

src/main/resources/
├── templates/              home, categories, products, product-detail, cart, checkout,
│   ├── admin/              order-confirmation, my-orders, login
│   └── fragments/          categories/*, products/*, navbar.html, messages.html
├── static/                 (optional custom CSS)
├── application.properties
├── application-prod.properties
├── data.sql
└── schema-backup.sql
```

---

## Tips
- **Cart in session:** use `@SessionAttributes` or a `@SessionScope` `@Component` — avoids DB reads on every page
- **CSRF:** disable only for REST API endpoints (stateless JWT), keep enabled for MVC forms
- **PayPal:** the JS SDK approach (client-side button + server-side capture) is simpler than the old REST SDK
- **SonarQube early:** run it at the end of Day 2 so Day 3 isn't all firefighting
- **Railway deployment:** keep H2 for local dev (`spring.profiles.active=` empty) and PostgreSQL for prod profile only