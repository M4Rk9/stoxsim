import type { Metadata } from "next";
import LegalDocument from "../components/LegalDocument";

export const metadata: Metadata = {
  title: "Cookie Notice | StoxSim",
  description: "How StoxSim uses essential cookies and browser storage.",
};

export default function CookiesPage() {
  return (
    <LegalDocument
      title="Cookie Notice"
      summary="StoxSim currently uses essential authentication storage and preference storage, not advertising cookies."
    >
      <section>
        <h2>1. Essential refresh cookie</h2>
        <p>
          The <strong>stoxsim_refresh</strong> cookie keeps you signed in and rotates your session
          securely. It is designed to be HttpOnly, Secure in HTTPS environments and SameSite Strict.
          It is necessary for authentication and cannot be disabled within StoxSim.
        </p>
      </section>
      <section>
        <h2>2. Browser storage</h2>
        <ul>
          <li>
            <strong>Session storage:</strong> holds the short-lived signed-in browser session and is
            cleared when the browser session ends or you sign out.
          </li>
          <li>
            <strong>Local storage:</strong> remembers the light, dark or system appearance preference.
          </li>
        </ul>
      </section>
      <section>
        <h2>3. No advertising cookies</h2>
        <p>
          The current public beta does not use advertising cookies or third-party behavioural
          advertising. If optional analytics or marketing technologies are introduced, this notice
          and the consent controls will be updated before they are enabled where consent is required.
        </p>
      </section>
      <section>
        <h2>4. Your controls</h2>
        <p>
          You can clear cookies and browser storage using browser settings. Clearing essential
          storage signs you out and may reset appearance preferences. Blocking the refresh cookie
          prevents the persistent sign-in flow from working.
        </p>
      </section>
      <section>
        <h2>5. Contact</h2>
        <p>
          Questions about storage technologies can be sent to
          <a href="mailto:privacy@stoxsim.com"> privacy@stoxsim.com</a>.
        </p>
      </section>
    </LegalDocument>
  );
}
