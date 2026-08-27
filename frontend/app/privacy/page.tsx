import type { Metadata } from "next";
import LegalDocument from "../components/LegalDocument";

export const metadata: Metadata = {
  title: "Privacy Notice | StoxSim",
  description: "How StoxSim collects, uses, stores and protects personal data.",
};

export default function PrivacyPage() {
  return (
    <LegalDocument
      title="Privacy Notice"
      summary="This notice explains what personal data StoxSim handles, why it is needed, and the choices available to you."
    >
      <section>
        <h2>1. Who is responsible</h2>
        <p>
          StoxSim is operated from India by the owner of the StoxSim service. For privacy
          questions or requests, email <a href="mailto:support.stoxsim@gmail.com">support.stoxsim@gmail.com</a>.
        </p>
      </section>
      <section>
        <h2>2. Data we collect</h2>
        <ul>
          <li>Account data: display name, email address, password hash and email-verification status.</li>
          <li>Security data: session identifiers, user-agent details and account-security events.</li>
          <li>Simulator data: virtual accounts, watchlists, simulated orders, trades, holdings, ledger entries and saved weekly report snapshots.</li>
          <li>Learning data: XP, levels, check-in streaks, completed missions and unlocked achievements.</li>
          <li>Communication preferences: whether weekly reports are enabled and the delivery timezone you select.</li>
          <li>Technical data: service logs, request timing, IP-derived security signals and error diagnostics.</li>
          <li>Communications you send to our support or privacy addresses.</li>
        </ul>
        <p>We do not ask for brokerage credentials, bank details or payment-card data.</p>
      </section>
      <section>
        <h2>3. Why we use it</h2>
        <ul>
          <li>Provide and secure your account and paper-trading portfolios.</li>
          <li>Send email verification, password-reset and essential security messages.</li>
          <li>Send optional weekly portfolio learning reports only when you explicitly enable them.</li>
          <li>Record educational progression and prevent duplicate mission or achievement awards.</li>
          <li>Detect abuse, enforce rate limits, troubleshoot failures and protect the service.</li>
          <li>Meet legal obligations, respond to valid requests and establish or defend legal claims.</li>
          <li>Improve reliability using aggregated or de-identified operational information.</li>
        </ul>
        <p>
          Registration requires your clear agreement to the Terms and consent to the processing
          needed to provide StoxSim. You may withdraw consent by deleting your account, although
          limited records may be retained where law or security requires it.
        </p>
      </section>
      <section>
        <h2>4. Sharing and service providers</h2>
        <p>
          We use infrastructure, email-delivery, database, cache, logging and market-data providers
          to operate StoxSim. They receive only the information reasonably needed for their role.
          We may also disclose information when legally required, to protect users or the service,
          or as part of a business transfer with appropriate safeguards. We do not sell personal data.
        </p>
      </section>
      <section>
        <h2>5. International processing</h2>
        <p>
          Some providers may process data outside your state or country. Where required, we use
          contractual and technical safeguards and assess applicable transfer restrictions.
        </p>
      </section>
      <section>
        <h2>6. Retention and security</h2>
        <p>
          Account and simulator data is kept while your account is active. Security and backup
          records may remain for a limited period after deletion to protect the service and meet
          legal requirements. Passwords are stored as hashes, refresh tokens are hashed and rotated,
          and production traffic is intended to use HTTPS. No system can guarantee absolute security.
        </p>
      </section>
      <section>
        <h2>7. Your choices and rights</h2>
        <p>
          Account settings let you update your profile, change your password, review or revoke
          sessions, export your account data and request permanent deletion. Depending on applicable
          law, you may also request access, correction, erasure or grievance handling. Email
          <a href="mailto:support.stoxsim@gmail.com"> support.stoxsim@gmail.com</a> from your registered address.
          We may need to verify your identity before acting.
        </p>
      </section>
      <section>
        <h2>8. Children</h2>
        <p>
          StoxSim is not intended for anyone under 18 or below the age of legal majority where they
          live. Do not create an account for a child.
        </p>
      </section>
      <section>
        <h2>9. Changes</h2>
        <p>
          Material changes will be dated on this page and, when appropriate, communicated in the
          service or by email. A new consent may be requested when a change materially affects how
          personal data is used.
        </p>
      </section>
    </LegalDocument>
  );
}
