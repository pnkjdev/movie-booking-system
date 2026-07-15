# Movie Ticket Booking System

Spring Boot backend for seat-level movie ticket booking at scale: multiple cities → theaters → screens → shows, time-bound seat holds with automatic expiry, tiered + weekend pricing, discount codes, idempotent payments, configurable refund policies, and non-blocking notifications.

**The core guarantee:** any number of users can race for the same seats and the system serializes them — exactly one wins, nobody is double-charged, and no seat is ever allocated twice. This is enforced at the database level and proven by multi-threaded integration tests.

---

## Quick start

Requirements: **JDK 17+** (nothing else — the DB is embedded).

```bash
./mvnw spring-boot:run
```

| What | Where |
|---|---|
| Swagger UI (all endpoints, try-it-out) | http://localhost:8080/swagger-ui.html |
| H2 console (`jdbc:h2:mem:moviedb`, user `sa`, empty password) | http://localhost:8080/h2-console |
| Seeded admin | `admin@moviebook.dev` / `Admin@123` |
| Seeded customer | `alice@example.com` / `Password@123` |

The seeder (disable with `app.seed.enabled=false`) creates 3 cities, 3 theaters, 4 screens with seat layouts, 3 movies, 7 upcoming shows (including weekend slots), 2 refund policies and 3 discount codes (`WELCOME10`, `FLAT50`, `LIMITED2`).

Run the tests:

```bash
./mvnw test        # 51 unit + integration tests, incl. concurrency races
```

## Five-minute API tour

```bash
# 1. Login (or POST /api/v1/auth/register to create an account)
TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"Password@123"}' | jq -r .token)

# 2. Browse: cities -> shows -> live seat map with per-seat prices
curl -s localhost:8080/api/v1/shows?cityId=1 | jq
curl -s localhost:8080/api/v1/shows/1/seats | jq

# 3. Hold seats (TTL-bound reservation; 409 lists contested seats if you lose a race)
curl -s -X POST localhost:8080/api/v1/holds -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"showId":1,"seatIds":[1,31,32]}' | jq

# 4. Create the booking from the hold, applying a discount code
curl -s -X POST localhost:8080/api/v1/bookings -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"holdId":1,"discountCode":"WELCOME10"}' | jq

# 5. Pay (idempotent: replaying the same idempotencyKey never double-charges;
#    set "simulateFailure": true to exercise the decline path)
curl -s -X POST localhost:8080/api/v1/bookings/1/payments -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"idempotencyKey":"pay-1","method":"UPI"}' | jq

# 6. History, notifications, cancellation (refund % follows the theater's policy)
curl -s localhost:8080/api/v1/bookings -H "Authorization: Bearer $TOKEN" | jq
curl -s localhost:8080/api/v1/notifications -H "Authorization: Bearer $TOKEN" | jq
curl -s -X POST localhost:8080/api/v1/bookings/1/cancel -H "Authorization: Bearer $TOKEN" | jq
```

## API surface

**Public (no login):** browse cities, theaters per city, movies, show search (`cityId`/`movieId`/`theaterId`/`date`), show details with availability, live seat map.

**Customer (JWT):** seat holds (create/inspect/release), bookings (create from hold, pay, cancel, history), notifications feed.

**Admin (JWT, `ROLE_ADMIN`):** cities, theaters (incl. refund-policy assignment), screens, bulk seat layouts, movies, show scheduling (with per-seat-type pricing and overlap rejection), show cancellation (mass refund), discount codes, refund policies.

Full parameter documentation lives in Swagger UI.

## Architecture

```
com.moviebooking
├── auth           users, roles, JWT issuance/verification
├── catalog        cities, theaters, screens, seats, movies, shows, per-show seat inventory
├── pricing        seat-type base prices + weekend surcharge
├── hold           time-bound seat holds, expiry sweeper
├── booking        bookings, payments (mock PSP), show cancellation
├── discount       discount codes with concurrency-safe limits
├── refund         configurable refund policies + refund math
├── notification   transactional outbox + async dispatcher + reminders
├── common/error   uniform error envelope, global exception handler
└── config         typed properties, security, clock, async, scheduling
```

### Booking lifecycle

```
seat map ──POST /holds──► HOLD (ACTIVE, TTL e.g. 7 min)
                            │ POST /bookings (+discount)
                            ▼
                       BOOKING (PENDING_PAYMENT)
                            │ POST /payments (idempotent)
              ┌─────────────┼──────────────┐
        gateway OK     gateway declined   TTL runs out
              ▼             ▼                  ▼
         CONFIRMED     stays payable        EXPIRED (seats freed,
       (seats BOOKED)  until hold TTL       discount redemption returned)
              │ POST /cancel (before showtime)
              ▼
         CANCELLED (refund % per policy, seats resellable)
```

### Concurrency design (the crux)

- Every show materializes one **`show_seats` inventory row per physical seat**. That row is the unit of contention.
- All writers (hold, confirm, release, cancel, sweep) take `SELECT … FOR UPDATE` locks on those rows **in deterministic id order** — competing requests serialize; deadlocks are structurally impossible. An `@Version` column backs this up as a second line of defence.
- Losers of a race get a clean `409 SEAT_UNAVAILABLE` listing exactly the contested seat labels.
- **Hold expiry is lazy-first**: an expired-but-unswept hold's seats are reclaimable immediately by the next request; the scheduled sweeper is an optimization/tidy-up, not a correctness gate.
- The **payment path and the sweeper both lock the hold row first** (payment: hold → booking; sweeper: hold → seats → booking), so "payment confirms" vs "hold expires" is decided by whoever wins the hold lock — never both.
- **Discount usage limits** are enforced with an atomic conditional `UPDATE … WHERE times_used < limit`; two parallel bookings cannot oversubscribe a code. Redemptions are returned if the unpaid booking dies.
- **Payment idempotency**: the client-supplied key is unique in the DB; replays return the original outcome, and a true concurrent duplicate collides on the unique constraint instead of charging twice.

### Pricing

`price(seat) = show.basePrice[seatType] + weekendSurcharge`. Admins set a base price per seat type (REGULAR / PREMIUM / RECLINER) when scheduling each show; shows starting Sat/Sun get a configurable surcharge (default 25%). Booked seats store a full price snapshot, so later catalog changes never rewrite history.

### Refund policies

A policy is an ordered set of rules — *"cancel ≥ N hours before the show → P% back"* — the most generous satisfied rule wins, no satisfied rule means no refund. Policies are admin-managed, assignable per theater, with a system-wide default fallback. Admin show cancellation bypasses the policy: every confirmed booking is refunded 100% and active holds are force-released.

### Notifications (never block the booking flow)

Outbox pattern: the notification row is written **inside the business transaction** (a rolled-back booking never notifies anyone), then an async dispatcher on a dedicated thread pool delivers it after commit, with a scheduled fallback that retries transient failures (3 attempts). Show reminders are queued by a scheduler for confirmed bookings entering the configurable lead window, exactly once per booking. The delivery channel is an interface; the take-home ships a logging email mock.

## Configuration

All knobs in `application.yml` under `app.*` (override via env vars):

| Property | Default | Meaning |
|---|---|---|
| `app.booking.hold-ttl-seconds` | 420 | Hold lifetime; payment must land inside it |
| `app.booking.max-seats-per-hold` | 10 | Per-hold seat cap |
| `app.pricing.weekend-surcharge-percent` | 25 | Sat/Sun surcharge |
| `app.jobs.hold-sweeper-interval-ms` | 15000 | Expired-hold sweep cadence |
| `app.jobs.reminder-lead-minutes` | 120 | "Show starts soon" window |
| `app.security.jwt-expiry-minutes` | 1440 | Token lifetime |
| `app.timezone` | Asia/Kolkata | Zone for show times, weekend & refund math |
| `app.seed.enabled` | true | Demo data on boot |

## Testing

`./mvnw test` — 51 tests.

- **Unit** (pure, no Spring): pricing math incl. weekend surcharge and rounding; refund tier selection incl. exact-boundary cases; discount validation matrix (unknown/inactive/expired/min-order/per-user/total-limit) and amount math.
- **Integration** (full context, real DB transactions): auth + RBAC over HTTP; complete booking journey over HTTP; idempotent payment replay; declined-payment retry; pay-after-expiry; hold conflict/reclaim/release cascades; refund tiers 100/75/0 + default-policy fallback; admin show cancellation.
- **Concurrency** (real threads, start barrier): 8 users race one seat → exactly 1 winner, 7 clean 409s, DB shows a single active hold; overlapping multi-seat requests never split a seat pair; a discount code with 1 remaining use survives a parallel redemption race.

## Assumptions & scope decisions

1. **H2 in-memory DB** for a zero-setup review experience. The concurrency design uses only standard row locking (`SELECT … FOR UPDATE`) and unique constraints, so it ports to PostgreSQL/MySQL by swapping the datasource; on a real deployment I'd add Flyway migrations instead of `ddl-auto`.
2. **Single time zone** (`app.timezone`) interprets show times, weekend detection and refund cutoffs. Multi-timezone theaters would attach a zone per city.
3. **Payment is mocked** behind a `PaymentGateway` interface — deterministic success, opt-in simulated declines, no real PSP webhooks. Refunds are recorded on the booking (amount + %) rather than moved through a ledger.
4. **Hold TTL covers seat selection through payment** (single 7-minute window, configurable). A declined payment can be retried until the TTL ends; the booking then expires with the hold.
5. **Cancellation rules**: only `CONFIRMED` bookings are cancellable (pending ones simply lapse or the hold is released), only before showtime, whole-booking only (no partial seat cancellation).
6. **Weekend pricing** is a global surcharge percent rather than a per-show flag — "weekend" as a pricing tier is time-derived, matching the brief; per-show base prices already give admins full price control.
7. **Discount redemption is reserved at booking creation** (atomically) and returned if the booking expires unpaid — the strictest interpretation that can't oversell a limited code. Cancelled-after-payment bookings keep their redemption.
8. **Notifications are "delivered" by logging** — the brief's requirement is the non-blocking delivery architecture (outbox + async dispatch + reminders), not a real SMTP integration.
9. **Layout immutability**: a screen's seat layout can't change once shows are scheduled on it (live inventory references physical seats). Movies soft-delete (deactivate) so history survives.
10. **Out of scope per the brief**: UI, containerization/CI, distributed deployment (single-node schedulers; the DB-level locking would still hold across replicas — only the sweeper/dispatcher would need leader election or `SKIP LOCKED`), OAuth/SSO/MFA, observability stacks.

## Tech stack

**Java 17 + Spring Boot 3.5** (Web, Data JPA, Security, Validation), **H2**, **JJWT**, **springdoc-openapi**, **Lombok**, **JUnit 5 / Mockito / Awaitility**. Chosen for: mandated stack (Spring Boot), mature transaction/locking semantics through JPA, stateless JWT fitting a scalable API, and a fully self-contained reviewer experience.

## Repository notes

- `docs/Movie Ticket Booking System.pdf` — the original requirement document (raw file used during development).
- `docs/AI-WORKFLOW.md` — how the AI-assisted workflow was run: priorities, human-set invariants, review interventions, and verification discipline.
- `CLAUDE.md` — the AI-agent guidance file used during development (also serves as the Agents.md).
- Commit history reflects the actual build order: scaffold → domain → security → catalog → discounts → holds → notifications → booking/payment → refunds/cancellation → seed → tests → docs.
