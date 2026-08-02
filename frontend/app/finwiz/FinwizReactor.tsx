"use client";

import type { CSSProperties, KeyboardEvent } from "react";
import { useState } from "react";
import styles from "./finwiz.module.css";

export type Topic =
  | "LEARN"
  | "STOCK_FUNDAMENTALS"
  | "TECHNICAL_ANALYSIS"
  | "FUNDAMENTAL_ANALYSIS"
  | "VALUATION"
  | "CASH_FLOW"
  | "MARKET_EVALUATION"
  | "PORTFOLIO_EDUCATION";

export interface TopicDefinition {
  id: Topic;
  label: string;
  shortLabel: string;
  description: string;
  prompt: string;
}

export const topics: TopicDefinition[] = [
  {
    id: "LEARN",
    label: "Learn the basics",
    shortLabel: "BASICS",
    description: "Understand exchanges, orders, risk and the language beginners meet first.",
    prompt: "Explain how a beginner should analyse a stock step by step.",
  },
  {
    id: "STOCK_FUNDAMENTALS",
    label: "Stock fundamentals",
    shortLabel: "STOCK DATA",
    description: "Study the business model, sector, ratios and the quality of a company.",
    prompt: "Teach me how to understand a company's business, financial ratios and competitive position.",
  },
  {
    id: "TECHNICAL_ANALYSIS",
    label: "Technical analysis",
    shortLabel: "TECHNICAL",
    description: "Read trends, moving averages, momentum, support and resistance without treating them as predictions.",
    prompt: "Explain technical analysis to a beginner using trend, moving averages, RSI, support and resistance.",
  },
  {
    id: "FUNDAMENTAL_ANALYSIS",
    label: "Fundamental analysis",
    shortLabel: "FINANCIALS",
    description: "Connect income statements, balance sheets, profitability, debt and growth.",
    prompt: "Show me a beginner-friendly framework for fundamental analysis using the three financial statements.",
  },
  {
    id: "VALUATION",
    label: "Valuation",
    shortLabel: "VALUATION",
    description: "Understand what assumptions a price may already contain and when ratios can mislead.",
    prompt: "Explain valuation using P/E, P/S and EV/EBITDA, including their limitations.",
  },
  {
    id: "CASH_FLOW",
    label: "Cash flows",
    shortLabel: "CASH FLOW",
    description: "Separate operating, investing and financing cash flows and understand free cash flow.",
    prompt: "Explain operating, investing and financing cash flow with a simple example.",
  },
  {
    id: "MARKET_EVALUATION",
    label: "Market evaluation",
    shortLabel: "MARKET",
    description: "Evaluate breadth, rates, earnings, volatility and sector leadership together.",
    prompt: "Teach me how to evaluate the overall market without relying on one index or one trading day.",
  },
  {
    id: "PORTFOLIO_EDUCATION",
    label: "Portfolio education",
    shortLabel: "PORTFOLIO",
    description: "Learn diversification, concentration, sizing, correlation and drawdown.",
    prompt: "Explain how a beginner can evaluate portfolio diversification, concentration and drawdown.",
  },
];

function polarPoint(radius: number, angle: number) {
  const radians = (angle - 90) * Math.PI / 180;
  return {
    x: 320 + radius * Math.cos(radians),
    y: 320 + radius * Math.sin(radians),
  };
}

function segmentPath(index: number) {
  const startAngle = index * 45 + 2.4;
  const endAngle = (index + 1) * 45 - 2.4;
  const outerStart = polarPoint(224, startAngle);
  const outerEnd = polarPoint(224, endAngle);
  const innerEnd = polarPoint(132, endAngle);
  const innerStart = polarPoint(132, startAngle);

  return [
    `M ${outerStart.x} ${outerStart.y}`,
    `A 224 224 0 0 1 ${outerEnd.x} ${outerEnd.y}`,
    `L ${innerEnd.x} ${innerEnd.y}`,
    `A 132 132 0 0 0 ${innerStart.x} ${innerStart.y}`,
    "Z",
  ].join(" ");
}

function splitLabel(label: string) {
  const words = label.split(" ");
  if (words.length === 1) return [label];
  const midpoint = Math.ceil(words.length / 2);
  return [words.slice(0, midpoint).join(" "), words.slice(midpoint).join(" ")];
}

export default function FinwizReactor({
  selected,
  onSelect,
}: {
  selected: Topic;
  onSelect: (topic: TopicDefinition) => void;
}) {
  const [hovered, setHovered] = useState<Topic | null>(null);
  const active = topics.find((item) => item.id === (hovered ?? selected)) ?? topics[0];

  function activate(event: KeyboardEvent<SVGGElement>, topic: TopicDefinition) {
    if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      onSelect(topic);
    }
  }

  return <section className={styles.reactorModule} aria-label="Finwiz learning reactor">
    <div className={styles.reactorHalo} aria-hidden="true" />
    <svg className={styles.reactor} viewBox="0 0 640 640" role="img" aria-label="Interactive Finwiz topic selector">
      <defs>
        <radialGradient id="finwiz-core" cx="50%" cy="46%" r="58%">
          <stop offset="0%" stopColor="var(--reactor-core)" stopOpacity="0.98" />
          <stop offset="58%" stopColor="var(--reactor-core-deep)" stopOpacity="0.95" />
          <stop offset="100%" stopColor="var(--reactor-core-deep)" stopOpacity="0.72" />
        </radialGradient>
        <linearGradient id="finwiz-segment" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="var(--reactor-segment-start)" />
          <stop offset="100%" stopColor="var(--reactor-segment-end)" />
        </linearGradient>
        <filter id="finwiz-glow" x="-40%" y="-40%" width="180%" height="180%">
          <feGaussianBlur stdDeviation="8" result="blur" />
          <feMerge>
            <feMergeNode in="blur" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
      </defs>

      <circle className={styles.orbitOuter} cx="320" cy="320" r="258" />
      <circle className={styles.orbitDash} cx="320" cy="320" r="246" />
      <circle className={styles.orbitInner} cx="320" cy="320" r="118" />

      {topics.map((topic, index) => {
        const midpoint = index * 45 + 22.5;
        const labelPoint = polarPoint(178, midpoint);
        const liftRadians = (midpoint - 90) * Math.PI / 180;
        const liftX = Math.cos(liftRadians) * 12;
        const liftY = Math.sin(liftRadians) * 12;
        const lines = splitLabel(topic.shortLabel);
        const isSelected = selected === topic.id;
        const isHovered = hovered === topic.id;

        return <g
          key={topic.id}
          role="button"
          tabIndex={0}
          aria-label={`Select ${topic.label}`}
          aria-pressed={isSelected}
          className={`${styles.reactorSegment} ${isSelected ? styles.reactorSegmentActive : ""} ${isHovered ? styles.reactorSegmentHover : ""}`}
          style={{ "--lift-x": `${liftX}px`, "--lift-y": `${liftY}px` } as CSSProperties}
          onClick={() => onSelect(topic)}
          onKeyDown={(event) => activate(event, topic)}
          onMouseEnter={() => setHovered(topic.id)}
          onMouseLeave={() => setHovered(null)}
          onFocus={() => setHovered(topic.id)}
          onBlur={() => setHovered(null)}
        >
          <path d={segmentPath(index)} />
          <circle cx={labelPoint.x} cy={labelPoint.y - 3} r="18" />
          <text x={labelPoint.x} y={labelPoint.y + (lines.length === 1 ? 3 : -2)} textAnchor="middle">
            {lines.map((line, lineIndex) => <tspan
              key={line}
              x={labelPoint.x}
              dy={lineIndex === 0 ? 0 : 12}
            >{line}</tspan>)}
          </text>
        </g>;
      })}

      <g className={styles.reactorCore} filter="url(#finwiz-glow)">
        <circle cx="320" cy="320" r="108" fill="url(#finwiz-core)" />
        <circle className={styles.coreRingOne} cx="320" cy="320" r="90" />
        <circle className={styles.coreRingTwo} cx="320" cy="320" r="71" />
        <circle className={styles.corePulse} cx="320" cy="320" r="49" />
        <text className={styles.coreTitle} x="320" y="309" textAnchor="middle">FINWIZ</text>
        <text className={styles.coreSubtitle} x="320" y="337" textAnchor="middle">AI</text>
        <text className={styles.coreStatus} x="320" y="365" textAnchor="middle">LEARNING CORE ONLINE</text>
      </g>
    </svg>

    <div className={styles.reactorReadout} aria-live="polite">
      <span>ACTIVE MODULE</span>
      <strong>{active.label}</strong>
      <p>{active.description}</p>
      <button type="button" onClick={() => onSelect(active)}>Load this module</button>
    </div>
  </section>;
}
