const markets = [
  {
    code: "IN",
    name: "Indian Market",
    exchanges: "NSE · BSE",
    balance: "₹5,00,000",
    indices: "NIFTY 50 · SENSEX · NIFTY BANK",
  },
  {
    code: "US",
    name: "United States",
    exchanges: "NASDAQ · NYSE",
    balance: "$10,000",
    indices: "S&P 500 · NASDAQ-100 · DOW",
  },
];

export default function Home() {
  return (
    <main>
      <nav className="nav">
        <a className="brand" href="#" aria-label="StoxSim home">
          <span className="brandMark">S</span>
          <span>Stox<span className="accent">Sim</span></span>
        </a>
        <div className="navActions">
          <button className="textButton" type="button">Sign in</button>
          <button className="primaryButton" type="button">Start practising</button>
        </div>
      </nav>

      <section className="hero">
        <div className="eyebrow">INDIA AND UNITED STATES</div>
        <h1>Learn the market<br />before risking money.</h1>
        <p>
          Research stocks, place realistic paper trades and understand your
          performance using separate virtual portfolios for two major markets.
        </p>
        <div className="heroActions">
          <button className="primaryButton large" type="button">Create free account</button>
          <a href="#markets">Explore markets</a>
        </div>
      </section>

      <section className="markets" id="markets" aria-labelledby="markets-title">
        <div className="sectionHeading">
          <span>Choose your market</span>
          <h2 id="markets-title">One simulator. Two market systems.</h2>
        </div>
        <div className="marketGrid">
          {markets.map((market) => (
            <article className="marketCard" key={market.code}>
              <div className="marketTop">
                <span className="marketCode">{market.code}</span>
                <span className="status">COMING SOON</span>
              </div>
              <h3>{market.name}</h3>
              <p>{market.exchanges}</p>
              <dl>
                <div>
                  <dt>Starting capital</dt>
                  <dd>{market.balance}</dd>
                </div>
                <div>
                  <dt>Track</dt>
                  <dd>{market.indices}</dd>
                </div>
              </dl>
            </article>
          ))}
        </div>
      </section>

      <section className="proof">
        <div><strong>Virtual capital</strong><span>Build confidence without financial risk.</span></div>
        <div><strong>Real market structure</strong><span>Sessions, orders, charges and currencies.</span></div>
        <div><strong>Portfolio analytics</strong><span>Understand returns, allocation and decisions.</span></div>
      </section>
    </main>
  );
}
