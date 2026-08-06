"use client";

import type { KeyboardEvent } from "react";
import styles from "./finwiz-tree.module.css";

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
  children: ChildSkill[];
}

const branches: SkillBranch[] = [
  {
    topic: "LEARN",
    x: 500,
    y: 100,
    labelX: 500,
    labelY: 148,
    children: [
      { x: 420, y: 35, label: "ORDERS", labelX: 390, labelY: 20, anchor: "end" },
      { x: 580, y: 35, label: "RISK", labelX: 610, labelY: 20, anchor: "start" },
    ],
  },
  {
    topic: "STOCK_FUNDAMENTALS",
    x: 700,
    y: 175,
    labelX: 758,
    labelY: 170,
    children: [
      { x: 790, y: 80, label: "BUSINESS", labelX: 817, labelY: 62, anchor: "start" },
      { x: 875, y: 165, label: "RATIOS", labelX: 905, labelY: 165, anchor: "start" },
    ],
  },
  {
    topic: "TECHNICAL_ANALYSIS",
    x: 810,
    y: 350,
    labelX: 866,
    labelY: 350,
    children: [
      { x: 920, y: 285, label: "TREND", labelX: 946, labelY: 270, anchor: "start" },
      { x: 920, y: 415, label: "MOMENTUM", labelX: 946, labelY: 430, anchor: "start" },
    ],
  },
  {
    topic: "FUNDAMENTAL_ANALYSIS",
    x: 700,
    y: 525,
    labelX: 758,
    labelY: 530,
    children: [
      { x: 790, y: 620, label: "INCOME", labelX: 817, labelY: 642, anchor: "start" },
      { x: 875, y: 535, label: "BALANCE", labelX: 905, labelY: 535, anchor: "start" },
    ],
  },
  {
    topic: "VALUATION",
    x: 500,
    y: 600,
    labelX: 500,
    labelY: 560,
    children: [
      { x: 420, y: 675, label: "MULTIPLES", labelX: 390, labelY: 692, anchor: "end" },
      { x: 580, y: 675, label: "ASSUMPTIONS", labelX: 610, labelY: 692, anchor: "start" },
    ],
  },
  {
    topic: "CASH_FLOW",
    x: 300,
    y: 525,
    labelX: 242,
    labelY: 530,
    children: [
      { x: 210, y: 620, label: "OPERATING", labelX: 183, labelY: 642, anchor: "end" },
      { x: 125, y: 535, label: "FREE CASH", labelX: 95, labelY: 535, anchor: "end" },
    ],
  },
  {
    topic: "MARKET_EVALUATION",
    x: 190,
    y: 350,
    labelX: 134,
    labelY: 350,
    children: [
      { x: 80, y: 285, label: "BREADTH", labelX: 52, labelY: 270, anchor: "start" },
      { x: 80, y: 415, label: "CYCLES", labelX: 52, labelY: 430, anchor: "start" },
    ],
  },
  {
    topic: "PORTFOLIO_EDUCATION",
    x: 300,
    y: 175,
    labelX: 242,
    labelY: 170,
    children: [
      { x: 210, y: 80, label: "SIZING", labelX: 183, labelY: 62, anchor: "end" },
      { x: 125, y: 165, label: "DIVERSIFY", labelX: 95, labelY: 165, anchor: "end" },
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
  return <section className={styles.treeModule} aria-label="Finwiz learning skill tree">
    <svg className={styles.skillTree} viewBox="0 0 1000 700" role="img" aria-label="Interactive Finwiz topic selector">
      <defs>
        <pattern id="finwiz-map-dots" width="8" height="8" patternUnits="userSpaceOnUse">
          <circle cx="1.5" cy="1.5" r="1" fill="#fff" />
        </pattern>
      </defs>

      <g className={styles.worldMap} aria-hidden="true">
        <path d="M70 180 130 120 230 105 315 145 360 205 325 250 270 260 230 310 170 300 125 250Z" />
        <path d="M285 60 345 55 380 95 345 125 300 105Z" />
        <path d="M330 325 390 350 425 430 405 520 365 610 325 535 305 445Z" />
        <path d="M450 165 520 145 585 175 570 225 510 240 465 215Z" />
        <path d="M465 245 560 240 625 300 610 405 555 525 485 455 450 340Z" />
        <path d="M575 165 675 110 810 120 925 180 965 250 900 305 810 290 730 340 650 305 600 245Z" />
        <path d="M750 465 835 430 925 470 905 545 815 570 745 525Z" />
      </g>

      <g className={styles.mapGuide} aria-hidden="true">
        <path d="M50 350h900" />
        <path d="M500 45v610" />
        <ellipse cx="500" cy="350" rx="430" ry="230" />
      </g>

      <g className={styles.treeFramework} aria-hidden="true">
        <circle cx="500" cy="350" r="137" />
        <circle cx="500" cy="350" r="116" />
        {Array.from({ length: 8 }, (_, index) => {
          const angle = index * 45 - 90;
          const radians = angle * Math.PI / 180;
          const x1 = 500 + Math.cos(radians) * 92;
          const y1 = 350 + Math.sin(radians) * 92;
          const x2 = 500 + Math.cos(radians) * 136;
          const y2 = 350 + Math.sin(radians) * 136;
          return <line key={angle} x1={x1} y1={y1} x2={x2} y2={y2} />;
        })}
      </g>

      {branches.map((branch, index) => {
        const topic = findTopic(branch.topic);
        const isActive = branch.topic === selected;
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
            aria-pressed={isActive}
            className={isActive ? styles.skillNodeActive : styles.skillNode}
            onClick={select}
            onKeyDown={(event) => activate(event, select)}
          >
            <circle className={styles.nodeOuter} cx={branch.x} cy={branch.y} r="25" />
            <circle className={styles.nodeInner} cx={branch.x} cy={branch.y} r="16" />
            <text className={styles.nodeIndex} x={branch.x} y={branch.y + 4} textAnchor="middle">
              {String(index + 1).padStart(2, "0")}
            </text>
            <text
              className={styles.nodeLabel}
              x={branch.labelX}
              y={branch.labelY}
              textAnchor="middle"
            >{topic.shortLabel}</text>
          </g>

          {branch.children.map((child) => <g
            key={`${branch.topic}-${child.label}-node`}
            role="button"
            tabIndex={0}
            aria-label={`${topic.label} subskill: ${child.label}`}
            className={isActive ? styles.childNodeActive : styles.childNode}
            onClick={select}
            onKeyDown={(event) => activate(event, select)}
          >
            <circle className={styles.childOuter} cx={child.x} cy={child.y} r="10" />
            <circle className={styles.childInner} cx={child.x} cy={child.y} r="3" />
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
        <circle className={styles.coreOuter} cx="500" cy="350" r="92" />
        <circle className={styles.coreMiddle} cx="500" cy="350" r="70" />
        <circle className={styles.coreInner} cx="500" cy="350" r="50" />
        <text className={styles.coreTitle} x="500" y="346" textAnchor="middle">FINWIZ</text>
        <text className={styles.coreSubtitle} x="500" y="369" textAnchor="middle">AI</text>
      </g>
    </svg>
  </section>;
}
