import type { Metadata } from "next";

import MarketingPage from "../components/MarketingPage";
import { createPublicMetadata } from "../seo";

export const metadata: Metadata = createPublicMetadata(
  "Free Paper Trading Simulator",
  "Practise paper trading in Indian and US markets with virtual money. Research stocks and ETFs, place simulated orders, and review your portfolio on StoxSim.",
  "/paper-trading",
);

export default function PaperTradingPage() {
  return (
    <MarketingPage
      eyebrow="FREE PAPER TRADING SIMULATOR"
      title="Practise trading before risking real money."
      introduction="StoxSim gives beginners a structured place to explore Indian and US stocks with virtual capital. Search instruments, place simulated orders and learn how portfolio decisions behave without connecting a brokerage account."
      highlights={[
        {
          title: "Virtual capital, real learning",
          body: "Use separate India and US practice portfolios so mistakes become lessons instead of financial losses.",
        },
        {
          title: "Market-aware simulation",
          body: "Follow market sessions, quotes and order states while remembering that provider data and simulated fills can be delayed or simplified.",
        },
        {
          title: "Review every decision",
          body: "Track holdings, returns and portfolio analytics to understand what changed after each simulated trade.",
        },
      ]}
      stepsTitle="A simple paper-trading routine"
      stepsIntroduction="StoxSim is designed to help a first-time learner move from curiosity to a repeatable practice habit."
      steps={[
        { title: "Choose a market", body: "Start with an Indian or US virtual portfolio and learn the currency, session and instruments." },
        { title: "Research before acting", body: "Search a stock or ETF, inspect its available quote, chart and company information, then form a reason for the trade." },
        { title: "Place and review", body: "Submit a simulated order and revisit the position later to compare the outcome with your original reasoning." },
      ]}
      note={<>Paper trading cannot reproduce every feature of real execution, including liquidity, slippage, taxes and market impact. StoxSim is an educational simulator—not a broker or investment adviser.</>}
    />
  );
}
