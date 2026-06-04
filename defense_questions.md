# Java Web Programming — Defense Questions

Compiled from all lecture materials. Questions marked **[VERY LIKELY]** are high-probability based on lecture emphasis (prof explicitly called out filters in class).

---

## FILTERS, INTERCEPTORS & LISTENERS (Lecture 12)

### Filters — **[VERY LIKELY]**

**Q: What is a Filter and where does it live — is it part of Spring?**
> A Filter is a servlet-level component from the **Jakarta Servlet API** (formerly `javax.servlet`). It lives in the **web container** (e.g. Apache Tomcat) and runs **outside the Spring MVC scope** — it is NOT part of Spring. Filters see every request before any Spring code runs, and also wrap the outgoing response.

**Q: What interface does a custom filter implement and what method must it override?**
> A filter implements `jakarta.servlet.Filter` and overrides `doFilter(ServletRequest req, ServletResponse res, FilterChain chain)`. You must call `chain.doFilter(req, res)` to forward the request to the next filter in the chain, otherwise the request is blocked.

**Q: What is `DelegatingFilterProxy` and why does it exist?**
> `DelegatingFilterProxy` is a plain servlet filter registered with the container that acts as a bridge — it hands control to the Spring-managed `FilterChainProxy`. This is how Spring Security plugs into the servlet filter chain without the container needing to know about Spring beans.

**Q: What is `FilterChainProxy` / `SecurityFilterChain`?**
> `FilterChainProxy` lives inside the Spring Application Context and manages an ordered list of Spring Security filters (`SecurityFilterChain`). Examples of filters inside it: `CsrfFilter`, `CorsFilter`, `UsernamePasswordAuthenticationFilter`, `BearerTokenAuthenticationFilter`, `ExceptionTranslationFilter`, `AuthorizationFilter`. Order matters — custom filters are added with `.addFilterBefore()` or `.addFilterAfter()`.

**Q: What is `OncePerRequestFilter` and why would you extend it for a custom JWT filter?**
> `OncePerRequestFilter` is a Spring convenience base class that guarantees the filter runs **exactly once per dispatch**, even on async or include/forward requests. You override `doFilterInternal()` instead of `doFilter()`. This is the recommended way to write a custom `JwtAuthFilter` because a JWT check should never run twice for the same request.

**Q: Name typical use cases for filters.**
> Authentication (decode JWT, populate SecurityContext), logging & tracing, image conversion, data compression (gzip), encryption, tokenization, event dispatching, XSLT filtering.

---

### Handler Interceptors

**Q: What is a Handler Interceptor and how is it different from a Filter?**
> An interceptor is a Spring MVC component that wraps every controller invocation. It lives **inside the Spring application context** (package `org.springframework.web.servlet`), so it is aware of Spring beans and can see the resolved handler method. A Filter runs in the container before Spring exists and only sees raw HTTP; an interceptor runs between the `DispatcherServlet` and the `@Controller`.

**Q: What are the three lifecycle methods of `HandlerInterceptor`?**
> - `preHandle(req, res, handler)` — runs BEFORE the controller. Returns `false` to short-circuit and skip the handler.
> - `postHandle(req, res, handler, modelAndView)` — runs AFTER the controller but BEFORE the view is rendered. Can add model attributes or tweak headers.
> - `afterCompletion(req, res, handler, ex)` — always runs after the view is rendered, even if an exception was thrown. Ideal for releasing resources, recording metrics.

**Q: How do you register a HandlerInterceptor?**
> Implement `HandlerInterceptor` and register it via `WebMvcConfigurer#addInterceptors(InterceptorRegistry)`.

**Q: When would you use an interceptor instead of a filter?**
> Use an interceptor when you need Spring context awareness — e.g. checking `@PreAuthorize` annotations programmatically, per-endpoint security policies, adding model attributes, timing per-controller metrics, or locale/tenant resolution. Use a filter for raw HTTP concerns that apply before Spring loads (auth token decoding, gzip, CORS at the container level).

---

### Listeners & Events

**Q: What is the Observer/event pattern in Spring and what classes are involved?**
> Events are published by any Spring bean using `ApplicationEventPublisher.publishEvent(event)`. Listeners react independently. Events extend `ApplicationEvent` (or can be plain POJOs since Spring 4.2). Listeners implement `ApplicationListener<E>` or use the `@EventListener` annotation on any bean method. The broker is `ApplicationEventMulticaster`.

**Q: What is `@TransactionalEventListener` and when would you use it?**
> It fires a listener only after a transaction commits (`AFTER_COMMIT`) or rolls back (`AFTER_ROLLBACK`). Use it when you want side-effects (sending emails, analytics events) to happen only if the DB transaction actually succeeded.

**Q: Name the common listener types and what triggers them.**
> - `ServletContextListener` — app startup/shutdown
> - `HttpSessionListener` — session created/destroyed
> - `HttpSessionAttributeListener` — session attribute added/replaced/removed
> - `ServletRequestListener` — each request initialized/destroyed
> - `ApplicationListener<ContextRefreshedEvent>` — Spring context fully loaded
> - `@EventListener` — any ApplicationEvent or POJO (modern preferred style)

---

### Filter vs Interceptor vs Listener — Comparison Table **[VERY LIKELY]**

| | Filter | Interceptor | Listener |
|---|---|---|---|
| Package | `jakarta.servlet` (Servlet API) | `org.springframework.web.servlet` | `org.springframework.context` |
| Defined in | Web container — outside Spring | Inside Spring application context | Inside Spring application context |
| Runs around | Servlet calls — before & after dispatch | Handler methods & view rendering | Published events |
| Sees the handler? | No — only raw HTTP | Yes | N/A — sees event payload |
| Typical use | Cross-cutting HTTP: auth, gzip, logging | Spring-aware pre/post logic & instrumentation | Decoupled domain notifications & side-effects |

---

## DATA VALIDATION

**Q: What is Bean Validation and how does Spring Boot enable it?**
> Bean Validation is the Jakarta standard for declaring constraints via annotations. Spring Boot auto-configures it via `spring-boot-starter-validation` (Hibernate Validator is the reference implementation). Annotations describe constraints; the framework enforces them automatically.

**Q: List common validation annotations and what they do.**
> - `@NotNull` — value must not be null
> - `@NotEmpty` — not null and size > 0 (String / Collection)
> - `@NotBlank` — not null, not empty, must have at least one non-whitespace char (String only)
> - `@Size(min, max)` — string/collection size within range
> - `@Min` / `@Max` — numeric bounds
> - `@Email` — valid email format (RFC 5322)
> - `@Pattern(regexp)` — matches regex (phone numbers, postal codes)
> - `@PositiveOrZero` / `@Positive` / `@Negative`
> - `@Past` / `@Future` — date/time constraints

**Q: What is `@Valid` and what is `BindingResult`?**
> `@Valid` placed before a method parameter triggers Bean Validation. `BindingResult` must immediately follow the validated parameter in the method signature — it captures all `FieldError` and `ObjectError` instances. `result.hasErrors()` returns true if any constraint was violated. If `BindingResult` is missing and validation fails, Spring throws `MethodArgumentNotValidException`.

**Q: Where should you put validation annotations — Command Object, Domain Object, or DTO?**
> - **Command Object** (MVC form backing object): keeps validation separate from domain, reusable per form.
> - **Domain Object** (@Entity directly): simple but mixes persistence and validation concerns.
> - **DTO** (REST API @RequestBody): clean API contract, decoupled from persistence layer — preferred for REST.

**Q: What is the difference between field-level and global (object-level) validation errors?**
> Field-level errors are attached to a specific property (e.g. email is empty). Global/ObjectErrors are not tied to a single field — they represent cross-field business rules (e.g. "phone must start with 385 for Croatia", "password and confirm password must match"). Added via `result.addError(new ObjectError(...))`.

**Q: How does validation work differently in MVC vs REST API controllers?**
> In MVC: use `@Valid @ModelAttribute`, capture errors in `BindingResult`, return the view name to re-render the form. In REST: use `@Valid @RequestBody`, omit `BindingResult`, let Spring throw `MethodArgumentNotValidException`, and handle it with `@ExceptionHandler` / `@RestControllerAdvice` returning a JSON list of `ErrorDto`.

**Q: How do you display validation errors in Thymeleaf?**
> - `th:errors="*{fieldName}"` — inline single field error
> - `th:each="err : ${#fields.errors('fieldName')}"` — iterate per-field errors
> - `${#fields.hasAnyErrors()}` + `${#fields.allErrors()}` — global error summary
> - `th:errorclass="error"` — automatically applies a CSS class when the field has errors

---

## DEPLOYMENT

**Q: What is the difference between JAR, WAR, and EAR?**
> - **JAR**: universal Java archive. Spring Boot produces a "fat JAR" (runnable, self-contained) with an embedded Tomcat. Runs with `java -jar`. Modern default.
> - **WAR**: Web Application Archive. Requires an external servlet container (Tomcat, Jetty). Contains a `WEB-INF/web.xml`. Used when deploying to existing infrastructure.
> - **EAR**: Enterprise Application Archive. Groups multiple WARs/JARs for full Jakarta EE servers (WildFly, WebSphere). Legacy only — new projects almost never use EARs.

**Q: What does the Spring Boot Maven plugin do?**
> It repackages the thin Maven JAR into a runnable "fat JAR" with all dependencies nested inside `BOOT-INF/lib/`. Key goals: `spring-boot:run` (run from source), `spring-boot:repackage` (builds the fat JAR), `spring-boot:build-image` (OCI image via Buildpacks — no Dockerfile needed), `spring-boot:start/stop` (for integration tests).

**Q: What is an embedded Tomcat and how does Spring Boot start it?**
> `spring-boot-starter-web` brings Apache Tomcat in as a regular JAR dependency. On startup, Spring Boot creates a `TomcatServletWebServerFactory`, instantiates an embedded Tomcat, configures connectors, and starts it on port 8080 by default — all inside the same JVM as your application code.

**Q: What three changes are needed to convert a Spring Boot project to a deployable WAR?**
> 1. Change `<packaging>jar</packaging>` to `<packaging>war</packaging>` in `pom.xml`.
> 2. Mark the embedded Tomcat as `<scope>provided</scope>` (the host container provides it).
> 3. Extend `SpringBootServletInitializer` in your main class so the servlet container can bootstrap the app.

---

## THYMELEAF & SPRING MVC INTEGRATION

**Q: What is the SpringStandard Dialect?**
> It extends Thymeleaf's standard dialect with Spring-specific features: Spring Expression Language (SpEL) for `${...}` and `*{...}` expressions, bean access via `${@beanName.method()}`, custom form attributes (`th:field`, `th:errors`, `th:errorclass`), and `#mvc.uri(...)` utility. `SpringTemplateEngine` automatically applies it.

**Q: What is the role of `ViewResolver` in Spring MVC?**
> `ViewResolver` maps the logical view name returned by a controller (e.g. `"users/list"`) to an actual `View` object (the Thymeleaf template). In Spring Boot it is auto-configured — templates live in `classpath:/templates/` with suffix `.html`.

**Q: What are Thymeleaf fragments and why are they useful?**
> A fragment is a reusable part of a template defined with `th:fragment="name"`. A controller can return only a fragment (`return "index :: content"`) instead of the full page. This is essential for AJAX / htmx partial page updates where you only need to re-render one section.

**Q: What is SpEL and how is it used in Thymeleaf?**
> Spring Expression Language (SpEL) is used inside `${...}` (variable expression — model attributes) and `*{...}` (selection expression — bound to the `th:object` on the form). It powers property access, method calls, arithmetic, and conditionals inside templates.

---

## JAVA DATABASE ACCESS TECHNOLOGIES

**Q: List the Java database access technologies from lowest to highest abstraction level.**
> 1. **Vanilla JDBC** — raw SQL, manual connection/ResultSet/exception handling, zero abstraction.
> 2. **JdbcTemplate** — Spring wrapper that removes boilerplate (auto connection mgmt, exception translation to `DataAccessException`, `RowMapper`).
> 3. **Spring Data JDBC** — lightweight ORM, maps objects via `@Table`/`@Column`, auto-generates CRUD repository, no lazy loading.
> 4. **Jakarta Persistence / Hibernate** — full ORM spec + implementation with lazy loading, 1st/2nd level cache, dirty checking, EntityManager.
> 5. **Spring Data JPA** — sits on top of JPA/Hibernate, auto-generates repositories, adds `Pageable`, `Specification`, auditing.

**Q: What is the main difference between Hibernate and Jakarta Persistence?**
> Jakarta Persistence (formerly JPA) is the **specification** (interface/contract) — it defines `@Entity`, `@Table`, JPQL, `EntityManager` lifecycle. Hibernate is the most popular **implementation** — it adds HQL, 2nd-level cache, batch inserts, and proxy objects for lazy loading. Other providers include EclipseLink, OpenJPA.

**Q: When would you choose Spring Data JDBC over Spring Data JPA?**
> Spring Data JDBC has no lazy loading, no session/persistence context, and always issues predictable queries. It follows DDD (Domain-Driven Design) principles with AggregateReference for relationships. Choose it for microservices and simple CRUD apps where you want no "magic". Choose Spring Data JPA for complex domains with rich relationships, where you benefit from dirty checking and caching.

**Q: What is the N+1 query problem in JPA and how do you avoid it?**
> When fetching a list of entities and accessing a lazy-loaded association, JPA issues 1 query for the list + N queries for each associated object. Avoid it with `JOIN FETCH` in JPQL, `@EntityGraph`, or `@Query` with fetch joins.

**Q: Why is JPA dramatically slower for bulk writes without configuration?**
> By default JPA inserts/updates one row per SQL statement without batching. Set `spring.jpa.properties.hibernate.jdbc.batch_size=25` (or similar) to enable batch inserts. Without this, inserting 10,000 rows can be ~5× slower than JdbcTemplate batch operations.

---

## SPRING SECURITY (from Lecture 12 context)

**Q: Explain the flow from an HTTP request to Spring Security authentication.**
> 1. Request hits the servlet container (Tomcat).
> 2. Passes through the servlet `FilterChain`.
> 3. Reaches `DelegatingFilterProxy`, which delegates to the Spring-managed `FilterChainProxy`.
> 4. `FilterChainProxy` executes the ordered `SecurityFilterChain` (CorsFilter → CsrfFilter → UsernamePasswordAuthenticationFilter / JwtAuthFilter → AuthorizationFilter).
> 5. If all filters pass, the `DispatcherServlet` dispatches to the handler.

**Q: What does `SecurityContextHolder` do and when is it populated?**
> `SecurityContextHolder` stores the current user's `Authentication` object (thread-local). It is populated by authentication filters (e.g. `JwtAuthFilter` calls `SecurityContextHolder.getContext().setAuthentication(auth)`). `SecurityContextHolderFilter` saves and restores it per request.

---

## QUICK FIRE — DEFINITIONS

| Term | Definition |
|---|---|
| `DispatcherServlet` | Spring's Front Controller — single entry point for all HTTP requests |
| `@Transactional` | Wraps a method in a DB transaction; rolls back on unchecked exceptions |
| `@RestController` | `@Controller` + `@ResponseBody` — returns data directly, not a view |
| `@RequestBody` | Deserializes HTTP request body (JSON) into a Java object |
| `@ResponseBody` | Serializes return value directly to the HTTP response body |
| `HikariCP` | Default Spring Boot connection pool — pools DB connections for reuse |
| `@SpringBootApplication` | Combines `@Configuration`, `@EnableAutoConfiguration`, `@ComponentScan` |
| Fat JAR | A runnable JAR with all dependencies nested inside `BOOT-INF/lib/` |
| `FilterChain.doFilter()` | Passes control to the next filter in the chain (or servlet if last) |
| `OncePerRequestFilter` | Base class ensuring a filter runs exactly once per dispatch |