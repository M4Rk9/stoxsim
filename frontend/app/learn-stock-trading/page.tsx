import type { Metadata } from "next";

import MarketingPage from "../components/MarketingPage";
import { createPublicMetadata } from "../seo";

export const metadata: Metadata = createPublicMetadata(
  "Learn Stock Trading with Virtual Money",
  "Learn stock-trading basics through virtual portfolios, guided milestones, watchlists and simulated trades across Indian and US markets with StoxSim.",
  "/learn-stock-trading",
);

export default function LearnStockTradingPage() {
  return (
    <MarketingPage
      eyebrow="LEARN STOCK TRADING"
      title="Turn market curiosity into a repeatable learning habit."
      introduction="StoxSim combines virtual portfolios with guided learning milestones. Beginners can observe stocks, practise simulated decisions and review outcomes at their own pace—without treating short-term profit as the only measure of progress."
      highlights={[
        {
          title: "Start with the foundations",
          body: "Learn how watchlists, orders, holdings, available cash and portfolio value relate to one another.",
        },
        {
          title: "Progress through practice",
          body: "Use guided missions and learning streaks to build consistency without tying every reward to trading volume or profit.",
        },
        {
          title: "Compare two markets",
          body: "Explore separate Indian and US portfolios while learning the differences in currencies, sessions and available instruments.",
        },
      ]}
      stepsTitle="Your first three learning milestones"
      stepsIntroduction="A beginner does not need to predict everything. Begin by developing a clear process you can explain and repeat."
      steps={[
        { title: "Observe", body: "Build a focused watchlist and notice how prices and market status change instead of chasing every mover." },
        { title: "Form a reason", body: "Study the information available in StoxSim and record what would make your idea right or wrong." },
        { title: "Review", body: "Use a simulated position to test the idea, then examine the result and improve the process." },
      ]}
      note={<>StoxSim provides general educational tools, not personalised investment recommendations. Always use independent research and qualified advice before making real financial decisions.</>}
    />
  );
}
