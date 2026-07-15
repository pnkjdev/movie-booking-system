# CLAUDE.md — AI agent guide for this repository

This file guided AI-assisted development (Claude Code) and doubles as `Agents.md`.

## Project

Movie Ticket Booking System — SDE-2 take-home. Spring Boot 3.5, Java 17, Maven, H2 + JPA, JWT security. Requirement document: `docs/Movie Ticket Booking System.pdf`.

## Commands

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)   # macOS: pin JDK 17
./mvnw compile              # fast compile check — run after every batch of edits
./mvnw test                 # full suite (51 tests) — must be green before commit
./mvnw spring-boot:run      # boot on :8080 with seeded demo data
```

Swagger: `http://localhost:8080/swagger-ui.html` · H2 console: `/h2-console` (`jdbc:h2:mem:moviedb`, `sa`, empty).
Seeded logins: `admin@moviebook.dev`/`Admin@123`, `alice@example.com`/`Password@123`.

## Architecture map

Package-per-domain under `com.moviebooking`: `auth`, `catalog`, `pricing`, `hold`, `booking`, `discount`, `refund`, `notification`, `common/error`, `config`, `seed`. Controllers → services → repositories; DTOs are records with static `from(entity)` mappers; services own transactions (`open-in-view` is off — never touch lazy relations outside a service).

## Invariants — do not break these

1. **Seat state transitions only under ordered row locks.** Every writer goes through `ShowSeatRepository.lockByShowIdAndSeatIds` / `lockByHoldId` (`PESSIMISTIC_WRITE`, `ORDER BY id`). Never update a `ShowSeat` from an unlocked read.
2. **Lock ordering: HOLD before BOOKING.** The payment path and the expiry sweeper both take the `SeatHold` row lock first. Adding a path that locks booking → hold reintroduces the ABBA deadlock.
3. **Expiry is lazy-first.** Correctness never depends on the sweeper; any reader/writer must treat `HELD` + expired hold as available (see `isEffectivelyAvailable`).
4. **Money is `BigDecimal` scale 2, `HALF_UP`**, snapshotted onto `BookingSeat` at purchase. Historical bookings are immutable.
5. **Discount limits via atomic conditional UPDATE** (`tryConsume`/`releaseOne`) — no check-then-act on `timesUsed`.
6. **Notifications via outbox**: row written inside the business transaction, delivery async after commit. Never call a channel synchronously from a request path.
7. **All business errors are `ApiException`** with an `ErrorCode`; the global handler renders the uniform envelope. Don't throw raw exceptions from services; don't throw after persisting a payment record (return the failed status instead — throwing rolls it back).
8. **Reserved words**: H2 rejects `VALUE` as a column name (bit us once — `discount_value`). Name columns explicitly when in doubt.

## Conventions

- Entities: Lombok `@Getter @Setter @Builder`, protected no-arg ctor, no Lombok `equals`/`hashCode`, explicit `@Table`/`@Column` names, plural snake_case tables.
- Validation at the DTO boundary with jakarta annotations; business rules in services.
- Time: inject the `Clock` bean everywhere (tests rely on it); show times are `LocalDateTime` in `app.timezone`, machine timestamps are `Instant`.
- Config: every business knob lives in typed `AppProperties` (`app.*`), never hard-coded.
- Tests: unit tests are Spring-free; integration tests extend `IntegrationTestBase` (profile `test`, seeder off, background jobs effectively disabled — drive expiry/dispatch explicitly). Build fixtures with `TestDataFactory` (unique suffixes; shared context, no `@DirtiesContext`).
- **Tests must not depend on the calendar day they run.** Fixture shows are scheduled relative to *now*, so "show in 48h" lands on a Saturday some days and picks up the 25% weekend surcharge (bit us once: two green tests turned red the next day). Never hard-code rupee amounts in integration tests — use `IntegrationTestBase.weekendAware(...)` / `percentOf(...)`; exact price math belongs in `PricingServiceTest` with pinned dates.
- Commits: imperative `feat:`/`test:`/`chore:` messages explaining the why; each commit compiles.

## Workflow used during development

1. Read the requirement PDF; extracted priorities: correctness under concurrency > core flows > breadth of admin/customer features > docs.
2. Built in compile-checkpointed slices (scaffold → domain → security → catalog → discounts → holds → notifications → booking/payment → refunds → seed → tests → docs), committing each slice.
3. Booted the app and drove every flow over HTTP (hold conflicts, idempotent replay, declined payments, refund tiers, show cancellation) before writing the automated suite.
4. Skills/tooling: Claude Code (Fable 5) with plan-mode task tracking, parallel file writes, Bash-driven verification loops; Maven + curl + python3 for E2E checks.
