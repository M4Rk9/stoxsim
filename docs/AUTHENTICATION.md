# Authentication and Account Provisioning

## Endpoints

| Method | Endpoint | Authentication |
|---|---|---|
| POST | `/api/v1/auth/register` | Public |
| POST | `/api/v1/auth/login` | Public |
| POST | `/api/v1/auth/refresh` | Public |
| POST | `/api/v1/auth/logout` | Public |
| GET | `/api/v1/auth/me` | Bearer access token |

## Registration

Registration normalizes the email address, hashes the password with BCrypt and creates the user plus both virtual accounts in one transaction:

- India: ₹5,00,000 in INR
- United States: $10,000 in USD

A failure in any step rolls back the entire registration.

## Tokens

- Access tokens are signed HS256 JWTs and expire after 15 minutes.
- Refresh tokens are 384-bit random values.
- Only SHA-256 refresh-token hashes are stored.
- Refresh rotates the previous token.
- Logout revokes the supplied refresh token.
- Production must set a unique `JWT_SECRET` of at least 32 characters.

## Browser rotation

The web client retries an authenticated request once after a `401`. Concurrent failures share one in-flight refresh request, preventing a rotating refresh token from being consumed more than once. If another request has already installed a newer access token, late failures retry with that token instead of rotating again. A failed refresh clears the local session and returns the user to sign-in.

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
