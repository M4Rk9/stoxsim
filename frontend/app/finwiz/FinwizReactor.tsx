"use client";

import type { KeyboardEvent } from "react";
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

type TextAnchor = "start" | "middle" | "end";

interface ChildSkill {
  x: number;
  y: number;
  label: string;
  labelX: number;
  labelY: number;
  anchor: TextAnchor;
}

interface SkillBranch {
  topic: Topic;
  x: number;
  y: number;
  labelX: number;
  labelY: number;
  anchor: TextAnchor;
  children: ChildSkill[];
}

const branches: SkillBranch[] = [
  {
    topic: "LEARN",
    x: 500,
    y: 132,
    labelX: 500,
    labelY: 94,
    anchor: "middle",
    children: [
      { x: 426, y: 48, label: "ORDERS", labelX: 402, labelY: 28, anchor: "end" },
      { x: 574, y: 48, label: "RISK", labelX: 598, labelY: 28, anchor: "start" },
    ],
  },
  {
    topic: "STOCK_FUNDAMENTALS",
    x: 714,
    y: 190,
    labelX: 748,
    labelY: 181,
    anchor: "start",
    children: [
      { x: 790, y: 90, label: "BUSINESS", labelX: 812, labelY: 72, anchor: "start" },
      { x: 842, y: 172, label: "RATIOS", labelX: 866, labelY: 176, anchor: "start" },
    ],
  },
  {
    topic: "TECHNICAL_ANALYSIS",
    x: 836,
    y: 350,
    labelX: 874,
    labelY: 342,
    anchor: "start",
    children: [
      { x: 930, y: 286, label: "TREND", labelX: 954, labelY: 273, anchor: "start" },
      { x: 942, y: 414, label: "MOMENTUM", labelX: 966, labelY: 430, anchor: "start" },
    ],
  },
  {
    topic: "FUNDAMENTAL_ANALYSIS",
    x: 714,
    y: 510,
    labelX: 748,
    labelY: 527,
    anchor: "start",
    children: [
      { x: 790, y: 610, label: "INCOME", labelX: 812, labelY: 632, anchor: "start" },
      { x: 842, y: 528, label: "BALANCE", labelX: 866, labelY: 532, anchor: "start" },
    ],
  },
  {
    topic: "VALUATION",
    x: 500,
    y: 568,
    labelX: 500,
    labelY: 618,
    anchor: "middle",
    children: [
      { x: 426, y: 650, label: "MULTIPLES", labelX: 402, labelY: 676, anchor: "end" },
      { x: 574, y: 650, label: "ASSUMPTIONS", labelX: 598, labelY: 676, anchor: "start" },
    ],
  },
  {
    topic: "CASH_FLOW",
    x: 286,
    y: 510,
    labelX: 252,
    labelY: 527,
    anchor: "end",
    children: [
      { x: 210, y: 610, label: "OPERATING", labelX: 188, labelY: 632, anchor: "end" },
      { x: 158, y: 528, label: "FREE CASH", labelX: 134, labelY: 532, anchor: "end" },
    ],
  },
  {
    topic: "MARKET_EVALUATION",
    x: 164,
    y: 350,
    labelX: 126,
    labelY: 342,
    anchor: "end",
    children: [
      { x: 70, y: 286, label: "BREADTH", labelX: 46, labelY: 273, anchor: "end" },
      { x: 58, y: 414, label: "CYCLES", labelX: 34, labelY: 430, anchor: "end" },
    ],
  },
  {
    topic: "PORTFOLIO_EDUCATION",
    x: 286,
    y: 190,
    labelX: 252,
    labelY: 181,
    anchor: "end",
    children: [
      { x: 210, y: 90, label: "SIZING", labelX: 188, labelY: 72, anchor: "end" },
      { x: 158, y: 172, label: "DIVERSIFY", labelX: 134, labelY: 176, anchor: "end" },
    ],
  },
];

function findTopic(id: Topic) {
  return topics.find((topic) => topic.id === id) ?? topics[0];
}

function activate(event: KeyboardEvent<SVGGElement>, action: () => void) {
  if (event.key === "Enter" || event.key === " ") {
    event.preventDefault();
    action();
  }
}

export default function FinwizReactor({
  selected,
  onSelect,
}: {
  selected: Topic;
  onSelect: (topic: TopicDefinition) => void;
}) {
  const [hovered, setHovered] = useState<Topic | null>(null);
  const active = findTopic(hovered ?? selected);

  return <section className={styles.treeModule} aria-label="Finwiz learning skill tree">
    <svg className={styles.skillTree} viewBox="0 0 1000 700" role="img" aria-label="Interactive Finwiz topic selector">
      <g className={styles.treeFramework} aria-hidden="true">
        <circle cx="500" cy="350" r="146" />
        <circle cx="500" cy="350" r="126" />
        {Array.from({ length: 8 }, (_, index) => {
          const angle = index * 45 - 90;
          const radians = angle * Math.PI / 180;
          const x1 = 500 + Math.cos(radians) * 96;
          const y1 = 350 + Math.sin(radians) * 96;
          const x2 = 500 + Math.cos(radians) * 145;
          const y2 = 350 + Math.sin(radians) * 145;
          return <line key={angle} x1={x1} y1={y1} x2={x2} y2={y2} />;
        })}
      </g>

      {branches.map((branch, index) => {
        const topic = findTopic(branch.topic);
        const isActive = branch.topic === selected || branch.topic === hovered;
        const select = () => onSelect(topic);

        return <g key={branch.topic}>
          <line
            className={isActive ? styles.treeLineActive : styles.treeLine}
            x1="500"
            y1="350"
            x2={branch.x}
            y2={branch.y}
          />
          {branch.children.map((child) => <line
            key={`${branch.topic}-${child.label}`}
            className={isActive ? styles.childLineActive : styles.childLine}
            x1={branch.x}
            y1={branch.y}
            x2={child.x}
            y2={child.y}
          />)}

          <g
            role="button"
            tabIndex={0}
            aria-label={`Select ${topic.label}`}
            aria-pressed={selected === branch.topic}
            className={selected === branch.topic ? styles.skillNodeActive : styles.skillNode}
            onClick={select}
            onKeyDown={(event) => activate(event, select)}
            onMouseEnter={() => setHovered(branch.topic)}
            onMouseLeave={() => setHovered(null)}
            onFocus={() => setHovered(branch.topic)}
            onBlur={() => setHovered(null)}
          >
            <circle cx={branch.x} cy={branch.y} r="27" />
            <circle className={styles.nodeInner} cx={branch.x} cy={branch.y} r="17" />
            <text className={styles.nodeIndex} x={branch.x} y={branch.y + 4} textAnchor="middle">
              {String(index + 1).padStart(2, "0")}
            </text>
            <text
              className={styles.nodeLabel}
              x={branch.labelX}
              y={branch.labelY}
              textAnchor={branch.anchor}
            >{topic.shortLabel}</text>
          </g>

          {branch.children.map((child) => <g
            key={`${branch.topic}-${child.label}-node`}
            role="button"
            tabIndex={0}
            aria-label={`Select ${topic.label}: ${child.label}`}
            className={selected === branch.topic ? styles.childNodeActive : styles.childNode}
            onClick={select}
            onKeyDown={(event) => activate(event, select)}
            onMouseEnter={() => setHovered(branch.topic)}
            onMouseLeave={() => setHovered(null)}
            onFocus={() => setHovered(branch.topic)}
            onBlur={() => setHovered(null)}
          >
            <circle cx={child.x} cy={child.y} r="11" />
            <circle className={styles.childNodeInner} cx={child.x} cy={child.y} r="4" />
            <text
              className={styles.childLabel}
              x={child.labelX}
              y={child.labelY}
              textAnchor={child.anchor}
            >{child.label}</text>
          </g>)}
        </g>;
      })}

      <g className={styles.core} aria-hidden="true">
        <circle className={styles.coreOuter} cx="500" cy="350" r="100" />
        <circle className={styles.coreMiddle} cx="500" cy="350" r="77" />
        <circle className={styles.coreInner} cx="500" cy="350" r="55" />
        <path d="m500 309 35 61h-70l35-61Z" />
        <circle cx="500" cy="350" r="17" />
        <text className={styles.coreTitle} x="500" y="344" textAnchor="middle">FINWIZ</text>
        <text className={styles.coreSubtitle} x="500" y="366" textAnchor="middle">AI</text>
      </g>

      <g className={styles.activeReadout} aria-live="polite">
        <text x="500" y="475" textAnchor="middle">{active.label.toUpperCase()}</text>
        <text className={styles.activeDescription} x="500" y="495" textAnchor="middle">{active.description}</text>
      </g>
    </svg>
  </section>;
}
