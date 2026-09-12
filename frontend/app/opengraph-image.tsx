import { ImageResponse } from "next/og";

export const alt = "StoxSim — practise Indian and US stock markets with virtual money";
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

export default function OpenGraphImage() {
  return new ImageResponse(
    (
      <div
        style={{
          alignItems: "center",
          background: "linear-gradient(135deg, #f7f9f5 0%, #e2f4e7 55%, #dce9f1 100%)",
          color: "#17211d",
          display: "flex",
          height: "100%",
          justifyContent: "center",
          padding: "72px",
          width: "100%",
        }}
      >
        <div style={{ display: "flex", flexDirection: "column", maxWidth: "1000px" }}>
          <div style={{ display: "flex", fontSize: 44, fontWeight: 800, letterSpacing: "-2px" }}>
            Stox<span style={{ color: "#0b8f55" }}>Sim</span>
          </div>
          <div style={{ display: "flex", fontSize: 74, fontWeight: 850, letterSpacing: "-4px", lineHeight: 1.05, marginTop: 52 }}>
            Learn stock trading.<br />Risk no real money.
          </div>
          <div style={{ color: "#53635b", display: "flex", fontSize: 28, marginTop: 34 }}>
            A free paper-trading simulator for Indian and US markets.
          </div>
        </div>
      </div>
    ),
    size,
  );
}

