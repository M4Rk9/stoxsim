# Verification email delivery

SMTP acceptance is not proof of inbox delivery. The app now shows Spam/Junk and
resend guidance for unverified users, but no code change can force inbox placement.
Verification remains required; do not mark accounts verified without their token.

## Investigation: 15 October 2026

The repository's production example specifies `smtp.resend.com` and
`no-reply@mail.stoxsim.com`; these are examples, not evidence of the deployed sender.
A public Google DNS TXT lookup returned no SPF record at `stoxsim.com` or
`mail.stoxsim.com`. The organizational domain publishes DMARC with `p=quarantine`
and relaxed alignment. This is not sufficient to diagnose failed authentication:
the actual envelope sender can be a different subdomain and aligned DKIM alone
can satisfy DMARC. DKIM selectors and actual sending credentials were not available.
Do not blindly add a guessed SPF record or weaken DMARC.

## Confirm and repair the actual sender

1. From one affected verification email, inspect its original headers:
   `Authentication-Results`, `From`, `Return-Path`, and the `d=`/`s=` values in
   `DKIM-Signature`. Redact recipient details, message IDs and the entire verification
   URL/token before sharing evidence. Check `spf=pass`, `dkim=pass`, `dmarc=pass` and
   alignment with the visible From domain. Never share an SMTP password.
2. Inspect deployed `MAIL_HOST` and `MAIL_FROM` privately. If using a personal Gmail
   SMTP account, do not assume it can authenticate an arbitrary `@stoxsim.com` From.
   Use the authenticated sender or a properly configured verified-domain provider.
3. In the actual provider's domain dashboard, publish its exact DKIM and SPF /
   return-path records. Verify the selected From domain/subdomain. Merge authorized
   SPF senders into one record at the applicable name; do not create duplicate SPF
   records. Keep DMARC aligned and inspect its reports.
4. Disable provider click/open tracking on verification and reset messages so the
   security link remains direct. Review provider bounce/rejection/complaint events
   and sending reputation. Do not mass-resend verification messages to old accounts.
5. Send an explicitly requested verification to controlled Gmail and Outlook inboxes.
   Confirm authentication headers, inbox placement, token confirmation and expiry.
   Record the deployed revision and provider settings; a successful SMTP log alone
   does not close this task. These live email checks were not performed in this PR.

Reference: Google's sender guidance recommends SPF, DKIM and DMARC and explains
alignment: https://support.google.com/mail/answer/81126
