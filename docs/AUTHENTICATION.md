# Authentication and Account Provisioning

## Endpoints

| Method | Endpoint | Authentication |
|---|---|---|
| POST | `/api/v1/auth/register` | Public, rate limited |
| POST | `/api/v1/auth/login` | Public, rate limited |
| POST | `/api/v1/auth/refresh` | HttpOnly refresh cookie |
| POST | `/api/v1/auth/logout` | HttpOnly refresh cookie |
| GET | `/api/v1/auth/me` | Bearer access token |

## Registration

Registration normalizes the email address, hashes the password with BCrypt and creates the user plus both virtual accounts in one transaction:

- India: ₹5,00,000 in INR
- United States: $10,000 in USD

A failure in any step rolls back the entire registration.

## Token boundaries

- Access tokens are signed HS256 JWTs and expire after 15 minutes.
- Access tokens are kept in tab-scoped `sessionStorage`; they are not persisted in `localStorage`.
- Refresh tokens are 384-bit random values delivered only through an HttpOnly, SameSite=Strict cookie.
- The refresh cookie is Secure in staging/production and scoped to `/api/v1/auth`.
- Only SHA-256 refresh-token hashes are stored by the API.
- Refresh rotates the previous token and logout revokes it.
- Production must set a unique `JWT_SECRET` of at least 32 characters and `AUTH_COOKIE_SECURE=true`.

The request-body refresh token remains accepted temporarily for non-browser clients, but the web client never receives or stores it.

## Browser rotation

The web client retries an authenticated request once after a `401`. Concurrent failures in one tab share one in-flight refresh request. Refresh and logout calls use `credentials: "include"`, allowing the API to rotate or clear the HttpOnly cookie without exposing its value to JavaScript. A failed refresh clears the tab session and returns the user to sign-in.

## WebSocket authentication

The HTTP upgrade endpoint remains reachable so browsers can complete the WebSocket handshake. The first STOMP `CONNECT` frame must include `Authorization: Bearer <access-token>`. The API validates the JWT before allowing subscriptions to market topics; missing, expired, or invalid tokens are rejected.

## Abuse protection

Redis-backed fixed-window limits protect authentication, refresh, FinWiz, trading writes, and general API traffic. Limits are returned through `X-RateLimit-*` headers. Exceeded requests receive HTTP `429` with `Retry-After`. Redis failures fail open and are logged so a cache outage does not take the trading simulator offline.

## Example

```http
POST /api/v1/auth/register
Content-Type: application/json

{
  "email": "marky@example.com",
  "password": "a-strong-password",
  "displayName": "Marky"
}
```
