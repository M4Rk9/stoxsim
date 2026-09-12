import type { MetadataRoute } from "next";

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "StoxSim — Stock Market Simulator",
    short_name: "StoxSim",
    description:
      "Practise Indian and US stock markets with virtual money and no real-money risk.",
    start_url: "/",
    display: "standalone",
    background_color: "#f4f6f1",
    theme_color: "#0b8f55",
    categories: ["finance", "education", "productivity"],
    icons: [
      {
        src: "/stoxsim-logo.png",
        sizes: "512x512",
        type: "image/png",
      },
    ],
  };
}

