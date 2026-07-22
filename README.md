# StoxSim

**Practise markets. Risk nothing.**

StoxSim is a multi-market paper-trading platform for Indian and United States stocks. Users receive separate virtual accounts, research equities and ETFs, submit simulated orders and measure portfolio performance without risking real money.

> This project is an educational simulator. It does not place real brokerage orders or provide investment advice.

## Markets

| India | United States |
|---|---|
| ₹5,00,000 virtual capital | $10,000 virtual capital |
| NSE equities and ETFs | NASDAQ and NYSE equities and ETFs |
| NIFTY 50, SENSEX and sector indices | S&P 500, NASDAQ-100 and Dow |
| Indian sessions and simulated charges | US sessions and simulated fees |

The accounts are independent. Balances, holdings and performance are never mixed across currencies.

## Technology

- Java 21 and Spring Boot 4.1
- Next.js 16 and TypeScript
- PostgreSQL and Flyway
- Redis
- Docker Compose
- GitHub Actions

## Repository

```text
stoxsim/
├── backend/          Spring Boot API
├── frontend/         Next.js application
├── docs/             Product and architecture decisions
├── .github/          CI workflows
└── docker-compose.yml
```

## Run locally

Requirements: Docker Desktop and Docker Compose.

```bash
cp .env.example .env
docker compose up --build
```

Then open:

- Web application: http://localhost:3000
- API status: http://localhost:8080/api/v1/system/status
- API health: http://localhost:8080/actuator/health

## Current milestone

The initial foundation includes:

- dual-market product definition
- modular-monolith architecture
- Spring Boot API shell
- Next.js landing experience
- PostgreSQL and Redis infrastructure
- first users/accounts migration
- CI checks for backend and frontend

Next: authentication and automatic creation of separate INR and USD virtual accounts.

## Documentation

- [Product definition](docs/PRODUCT.md)
- [Architecture](docs/ARCHITECTURE.md)

## License

MIT
