import type { Metadata } from "next";
import LegalDocument from "../components/LegalDocument";

export const metadata: Metadata = {
  title: "Risk Disclaimer | StoxSim",
  description: "Important limitations of StoxSim market data and simulated trading.",
};

export default function DisclaimerPage() {
  return (
    <LegalDocument
      title="Risk Disclaimer"
      summary="StoxSim is a learning tool. Nothing in the service is a recommendation to buy, sell or hold a security."
    >
      <section>
        <h2>No real trading</h2>
        <p>
          Every account balance, order, holding, trade and return shown by StoxSim is simulated.
          No brokerage order is placed and no money or security is held for you.
        </p>
      </section>
      <section>
        <h2>No investment advice</h2>
        <p>
          Charts, screeners, fundamentals, market movers, Finwiz responses and educational text are
          general information only. They do not consider your objectives, financial position, tax
          circumstances or risk tolerance. Obtain qualified professional advice before making real
          financial decisions.
        </p>
      </section>
      <section>
        <h2>Data limitations</h2>
        <p>
          Market and company data may be delayed, stale, incomplete, unavailable, adjusted differently
          by providers or simply wrong. A “LIVE” label describes the latest provider state observed by
          StoxSim; it is not a guarantee of exchange-level real-time accuracy.
        </p>
      </section>
      <section>
        <h2>Simulation limitations</h2>
        <p>
          Simulated fills do not fully reproduce liquidity, queue priority, partial fills, halts,
          auction mechanics, spread changes, slippage, taxes, brokerage policies or operational
          failures. Fee and tax schedules are educational estimates and may become outdated.
          Hypothetical performance has inherent limitations and is not evidence of future results.
        </p>
      </section>
      <section>
        <h2>Market risk</h2>
        <p>
          Real securities can lose value rapidly, and past performance does not predict future
          performance. Never treat virtual gains, rankings or AI-generated explanations as assurance
          that a strategy will work with real money.
        </p>
      </section>
      <section>
        <h2>Contact</h2>
        <p>
          Report a material data or content issue to
          <a href="mailto:support@stoxsim.com"> support@stoxsim.com</a>.
        </p>
      </section>
    </LegalDocument>
  );
}
