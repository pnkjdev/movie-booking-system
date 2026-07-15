# AI-Assisted Development Workflow

This document describes how I built this project using Claude Code (Anthropic's Fable 5 model) as an AI pair, and — more importantly — where the human judgment sat in that loop. The short version: **the AI typed most of the code; I owned the priorities, the scoping decisions, the correctness bar, the review, and the verification.** Every architectural invariant in this codebase exists because it was demanded, checked, or corrected by a human in the loop.

## Tooling

| Tool | Role |
|---|---|
| Claude Code (Fable 5) | Code generation, refactoring, test writing, E2E verification loops |
| `CLAUDE.md` | The standing agent guide: build commands, architecture map, invariants the AI must not break (checked into this repo, doubles as Agents.md) |
| Claude Code skills used | Plan-mode task tracking (10-task build plan), parallel file writes, Bash-driven compile/test/verify loops |
| Maven + curl + python3 | Compile checkpoints and live HTTP verification of every flow |
| H2 console + Swagger UI | Manual inspection of seat/hold/booking state mid-flow |

## The workflow, phase by phase

### 1. Requirement analysis before any code
I started by having the requirement PDF read and distilled into an explicit priority order, which I set as the standing directive for the whole build:

> correctness under concurrency → core flows (hold → book → pay → cancel) → breadth of admin/customer features → docs

Every subsequent trade-off (e.g., how much time to spend on locking design vs. more admin endpoints) was resolved against this ordering.

### 2. Slice plan with compile checkpoints
The build was decomposed into dependency-ordered slices — scaffold → config/errors → domain model → security → pricing → catalog → discounts → holds → notifications → refunds → booking/payment → seed → tests → docs. My rule for the loop: **every slice must compile before it lands, and every commit must build.** The AI ran `./mvnw compile` after each batch of edits and was not allowed to move on red.

### 3. Invariants set up front (the human-defined correctness bar)
I required these properties before implementation started, and they are documented in `CLAUDE.md` so the AI could not drift from them in later edits:

1. No double allocation ever — all seat state transitions under `SELECT … FOR UPDATE` row locks taken in deterministic id order.
2. A single lock ordering across the codebase (hold before booking) so payment confirmation and hold expiry cannot deadlock.
3. Expiry must be lazy-first — correctness can never depend on a background sweeper.
4. Money is `BigDecimal` scale-2 `HALF_UP`, snapshotted at purchase; history is immutable.
5. Discount limits via atomic conditional UPDATE — no check-then-act.
6. Notifications via transactional outbox — nothing in the booking path may block on delivery.
7. One uniform error envelope; machine-readable error codes.

### 4. Verify like an outsider, not like the author
After the code existed, I had the running app driven over plain HTTP (curl) through every flow before trusting any of it: hold conflicts between two users, declined payment → retry, idempotency-key replay, refund tiers at different lead times, admin show cancellation with mass refunds. Only after the manual pass did the automated suite get written — 51 unit + integration tests, including real multi-threaded races (8 users contending for one seat; concurrent redemption of a discount code with one use left).

### 5. Review interventions that changed the code
This is the part that matters for "did the human actually steer":

- **Caught two failing tests and pushed for root cause, not a patch.** Two booking-flow tests went red a day after they were written. The investigation I asked for showed the product code was *correct*: test fixtures schedule shows relative to "now", the +48h show had drifted onto a Saturday, and the weekend surcharge correctly fired against hard-coded weekday expectations. I had the fix done properly — weekend-aware assertions everywhere, two more latent date-dependent tests defused before they could fire, and the trap documented in `CLAUDE.md` so it can't be reintroduced.
- **Demanded comprehension walkthroughs.** Before recording the demo I had the whole system explained back to me over live requests — what happens at boot, what each API call changes in the database, where the async notification actually runs, why the seat map shows an expired hold as available. If I couldn't explain it, it didn't ship.
- **Owned the delivery process manually.** The git history was rebuilt by hand at the end: repo initialized blank, remote and SSH identity configured (including untangling a two-GitHub-account situation on my machine), and the project committed in 16 dependency-ordered steps — each one compiling — so the history reflects the real build order.
- **Environment fixes.** JDK pinning (machine defaults to Java 19/26; project needs 17) and an H2 reserved-word collision (`VALUE` → `discount_value`) were found by running things, not by reading generated code.

### 6. Scoping decisions (mine, documented in the README)
H2 over Postgres for a zero-setup review; mock PSP behind a `PaymentGateway` interface; single configured timezone; hold TTL spanning selection-through-payment; whole-booking cancellation only; logging channel for notifications; seat layouts frozen once shows exist. Each of these is a deliberate trade-off recorded in the README's assumptions section.

## Prompting patterns that worked

- **Give priorities, not task lists.** One standing priority order beats fifty micro-instructions; the AI can resolve its own trade-offs against it.
- **Make invariants standing context.** Rules that live in `CLAUDE.md` survive across sessions and edits; rules that live in one prompt don't.
- **Compile/test gates between slices.** Small verified steps localize failures to the slice that caused them.
- **Ask "show me it working", never accept "it should work".** Live HTTP against the running app caught what code review alone would not (the H2 reserved word bit exactly there).
- **Treat AI output as a PR from a fast, confident colleague** — review it, interrogate it, and make it explain itself before merging.

## Mapping to the video talking points

| Required topic | Where it's covered |
|---|---|
| Approach & solution overview | README → Architecture + Booking lifecycle |
| Tech stack & reasoning | README → Tech stack, Assumptions #1 |
| AI workflow | This document |
| Testing approach | README → Testing; `src/test/**`; §4–5 above |
