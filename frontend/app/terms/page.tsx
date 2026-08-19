import type { Metadata } from "next";
import LegalDocument from "../components/LegalDocument";

export const metadata: Metadata = {
  title: "Terms of Use | StoxSim",
  description: "Rules for using the StoxSim educational paper-trading service.",
};

export default function TermsPage() {
  return (
    <LegalDocument
      title="Terms of Use"
      summary="These terms govern access to the StoxSim public beta and its educational paper-trading features."
    >
      <section>
        <h2>1. Agreement and eligibility</h2>
        <p>
          By creating or using an account, you agree to these Terms and acknowledge the Privacy
          Notice and Risk Disclaimer. You must be at least 18 and legally able to enter this agreement.
          If you do not agree, do not use StoxSim.
        </p>
      </section>
      <section>
        <h2>2. Educational simulator only</h2>
        <p>
          StoxSim uses virtual money and simulated orders. It is not a broker, exchange, investment
          adviser, research analyst, portfolio manager or fiduciary. It does not hold customer funds,
          open brokerage accounts or send real orders to a market.
        </p>
      </section>
      <section>
        <h2>3. Your account</h2>
        <p>
          Provide accurate information, keep credentials confidential, verify your email and promptly
          report suspected misuse. You are responsible for activity through your account unless caused
          by our breach. One person may not use accounts to evade limits or disrupt the service.
        </p>
      </section>
      <section>
        <h2>4. Acceptable use</h2>
        <p>You must not:</p>
        <ul>
          <li>Break applicable law or another person&apos;s rights.</li>
          <li>Scrape, copy, resell or redistribute market data or other restricted content.</li>
          <li>Probe security, bypass access controls or overload the service.</li>
          <li>Use automated access except through an interface we expressly provide.</li>
          <li>Present simulated results as real brokerage performance.</li>
        </ul>
      </section>
      <section>
        <h2>5. Market data and simulated results</h2>
        <p>
          Quotes, fundamentals, calendars and other data may be supplied by third parties and may be
          delayed, stale, incomplete, unavailable or inaccurate. Simulated fills, slippage, charges,
          taxes and portfolio values are simplified estimates and can differ materially from real
          execution. Provider and exchange rights remain with their respective owners.
        </p>
      </section>
      <section>
        <h2>6. Intellectual property</h2>
        <p>
          StoxSim software, branding and original content are protected by applicable law and licences.
          Open-source components remain subject to their licences. These Terms grant only a limited,
          revocable, non-transferable right to use the service for personal educational purposes.
        </p>
      </section>
      <section>
        <h2>7. Public beta availability</h2>
        <p>
          The service may change, contain defects or be interrupted. We may impose limits, suspend
          features, refuse registration or terminate access to protect StoxSim, comply with law or
          address misuse. You may stop using the service and delete your account at any time.
        </p>
      </section>
      <section>
        <h2>8. Disclaimers and liability</h2>
        <p>
          To the maximum extent permitted by law, StoxSim is provided “as is” and “as available”
          without warranties of accuracy, availability, fitness or non-infringement. StoxSim is not
          responsible for trading decisions or financial loss based on the simulator. Liability that
          cannot legally be excluded remains limited as required by applicable law.
        </p>
      </section>
      <section>
        <h2>9. Governing law and contact</h2>
        <p>
          These Terms are governed by the laws of India, without overriding mandatory rights that
          apply where you live. Before starting formal proceedings, contact
          <a href="mailto:support@stoxsim.com"> support@stoxsim.com</a> so we can try to resolve the issue.
        </p>
      </section>
      <section>
        <h2>10. Changes</h2>
        <p>
          Updated Terms will show a new effective date. Material changes may require renewed acceptance.
          Continued use after an effective update constitutes acceptance where law permits.
        </p>
      </section>
    </LegalDocument>
  );
}
