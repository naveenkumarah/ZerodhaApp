# TradeDiscipline Pro (backend)

Rule-based discipline platform for positional option traders, integrated with Zerodha Kite Connect.
Capital protection is evaluated before profit.

## Stack
Java 21 · Spring Boot 3.3 · Spring Security + JWT (jjwt) · Spring Data JPA · MySQL 8 · Flyway · Maven · Docker

## Layout (`com.example`)
| Package | Responsibility |
|---|---|
| `auth` | Register/login (BCrypt + JWT), Kite OAuth callback (tokens stored AES-256-GCM encrypted) |
| `kite` | `KiteGateway` port + `KiteConnectGateway` (Kite Connect v3 REST) |
| `trading` | Risk-gated order placement, exits, margins, positions, orders, LTP |
| `risk` | Discipline rules (GREEN/YELLOW/RED), trade gate, risk profile, ledger-derived state |
| `sizing` | Fixed, percentage, volatility, ATR, Kelly sizing; max-exposure cap; lot rounding |
| `stoploss` | Initial stops (points/%/ATR); 1R cost, 2R lock 1R, 3R EMA50 trail, 5R SuperTrend trail |
| `margin` | Available/used margin parsing and SPAN/exposure/premium breakdown |
| `strategy` | Leg model, expiry payoff engine, breakevens, POP, Black-Scholes Greeks; 5 strategies |
| `journal` | Journal entries; deterministic behaviour analysis (emotion, overtrading, revenge, score) |
| `analytics` | Win rate, profit factor, expectancy, R:R, max drawdown, Sharpe, equity/monthly/strategy PnL |
| `common` | Token cipher, business-rule exception, API error handling |

## Key rules (configurable)
- Max 3 trades/day, lock after 2 consecutive losses (per day).
- Daily / weekly / monthly loss limits from the risk profile (default 3% / 6% / 10%); YELLOW at 2/3 of a limit.
- Pre-trade gate: checklist complete, stop defined, R:R >= 2, risk per trade <= profile %, lot size not increased after a loss, margin sufficient.
- Blocked orders never reach Zerodha.

## API
- `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/kite/login-url`, `POST /api/auth/kite/callback`
- `GET /api/trading/margins|positions|orders`, `GET /api/trading/ltp`, `POST /api/trading/order`, `PUT|DELETE /api/trading/order/{variety}/{orderId}`, `POST /api/trading/exit`
- `POST /api/risk/calculate-position-size`, `POST /api/risk/validate-trade`, `GET /api/risk/status`, `POST /api/risk/stop-loss/initial|evaluate`, `GET|PUT /api/risk/profile`
- `POST /api/strategy/iron-condor|bull-put|bear-call|hedged-strangle|iron-fly`
- `POST /api/journal/add`, `GET /api/journal/history`, `GET /api/journal/analysis`
- `GET /api/analytics/summary`

## Run
```bash
copy .env.example .env      # fill in secrets
run.bat --setup-db          # one-time: create MySQL database and user
run.bat                     # build and start the application
mvn test                    # unit tests
```

## Not yet implemented
- React frontend (Redux Toolkit, MUI, TradingView) and WebSocket live feed.
- Kafka events, Redis cache, Kubernetes manifests.
- Automated stop-loss execution (scheduler + market-data feed); the engine is available via `/api/risk/stop-loss/evaluate`.
- Kite option chain and historical data endpoints; Kite session refresh (Kite access tokens expire daily).
- Screenshot upload (journal stores URLs only).
- Integration tests against MySQL (Testcontainers).

## Note on the prompt's sizing example
The prompt states `1000000 × 1% / 10 = 100 shares`. The arithmetic gives **1000 shares** (budget ₹10,000 ÷ ₹10 risk per share). The engine returns 1000.

