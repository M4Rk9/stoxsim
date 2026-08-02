import type { ReactNode } from "react";
import styles from "./finwiz.module.css";

type Block =
  | { type: "heading"; level: number; text: string }
  | { type: "paragraph"; text: string }
  | { type: "bullets"; items: string[] }
  | { type: "numbers"; items: string[] }
  | { type: "formula"; text: string }
  | { type: "quote"; text: string }
  | { type: "divider" };

function cleanFormula(value: string) {
  return value
    .replace(/\$\$/g, "")
    .replace(/\\\[/g, "")
    .replace(/\\\]/g, "")
    .replace(/\\\(/g, "")
    .replace(/\\\)/g, "")
    .replace(/\\text\{([^{}]*)\}/g, "$1")
    .replace(/\\mathrm\{([^{}]*)\}/g, "$1")
    .replace(/\\frac\{([^{}]+)\}\{([^{}]+)\}/g, "($1 ÷ $2)")
    .replace(/\\sqrt\{([^{}]+)\}/g, "√($1)")
    .replace(/\\times/g, "×")
    .replace(/\\cdot/g, "·")
    .replace(/\\approx/g, "≈")
    .replace(/\\geq/g, "≥")
    .replace(/\\leq/g, "≤")
    .replace(/\\%/g, "%")
    .replace(/\\left|\\right/g, "")
    .replace(/[{}]/g, "")
    .replace(/\s+/g, " ")
    .trim();
}

function cleanText(value: string) {
  return cleanFormula(value)
    .replace(/^\s*#{1,6}\s*/, "")
    .replace(/\[(.*?)\]\((.*?)\)/g, "$1")
    .replace(/\*\*|__/g, "")
    .replace(/`/g, "")
    .replace(/(^|\s)\*(?=\S)/g, "$1")
    .replace(/(?<=\S)\*(?=\s|$)/g, "")
    .trim();
}

function formatInline(value: string): ReactNode[] {
  const nodes: ReactNode[] = [];
  const expression = /(\*\*.*?\*\*|__.*?__|`.*?`|\[.*?\]\(.*?\))/g;
  let cursor = 0;
  let match: RegExpExecArray | null;

  while ((match = expression.exec(value)) !== null) {
    if (match.index > cursor) {
      nodes.push(cleanText(value.slice(cursor, match.index)));
    }

    const token = match[0];
    if ((token.startsWith("**") && token.endsWith("**")) || (token.startsWith("__") && token.endsWith("__"))) {
      nodes.push(<strong key={`${match.index}-strong`}>{cleanText(token.slice(2, -2))}</strong>);
    } else if (token.startsWith("`") && token.endsWith("`")) {
      nodes.push(<code key={`${match.index}-code`}>{cleanText(token.slice(1, -1))}</code>);
    } else {
      const link = token.match(/^\[(.*?)\]\((.*?)\)$/);
      nodes.push(cleanText(link?.[1] ?? token));
    }
    cursor = expression.lastIndex;
  }

  if (cursor < value.length) nodes.push(cleanText(value.slice(cursor)));
  return nodes.filter((node) => node !== "");
}

function isStructure(line: string) {
  return /^(#{1,6})\s+/.test(line)
    || /^[-*•]\s+/.test(line)
    || /^\d+[.)]\s+/.test(line)
    || /^>\s?/.test(line)
    || /^[-_*]{3,}$/.test(line)
    || line.startsWith("$$");
}

function parseAnswer(answer: string): Block[] {
  const lines = answer.replace(/\r\n/g, "\n").split("\n");
  const blocks: Block[] = [];
  let index = 0;

  while (index < lines.length) {
    const line = lines[index].trim();
    if (!line) {
      index += 1;
      continue;
    }

    const heading = line.match(/^(#{1,6})\s+(.+)$/);
    if (heading) {
      blocks.push({ type: "heading", level: heading[1].length, text: cleanText(heading[2]) });
      index += 1;
      continue;
    }

    if (/^[-_*]{3,}$/.test(line)) {
      blocks.push({ type: "divider" });
      index += 1;
      continue;
    }

    if (line.startsWith("$$")) {
      const formula: string[] = [line];
      index += 1;
      while (index < lines.length && !formula.join("\n").trimEnd().endsWith("$$")) {
        formula.push(lines[index]);
        index += 1;
      }
      blocks.push({ type: "formula", text: cleanFormula(formula.join(" ")) });
      continue;
    }

    if (/^[-*•]\s+/.test(line)) {
      const items: string[] = [];
      while (index < lines.length) {
        const item = lines[index].trim().match(/^[-*•]\s+(.+)$/);
        if (!item) break;
        items.push(item[1]);
        index += 1;
      }
      blocks.push({ type: "bullets", items });
      continue;
    }

    if (/^\d+[.)]\s+/.test(line)) {
      const items: string[] = [];
      while (index < lines.length) {
        const item = lines[index].trim().match(/^\d+[.)]\s+(.+)$/);
        if (!item) break;
        items.push(item[1]);
        index += 1;
      }
      blocks.push({ type: "numbers", items });
      continue;
    }

    if (/^>\s?/.test(line)) {
      const quote: string[] = [];
      while (index < lines.length) {
        const item = lines[index].trim().match(/^>\s?(.*)$/);
        if (!item) break;
        quote.push(item[1]);
        index += 1;
      }
      blocks.push({ type: "quote", text: quote.join(" ") });
      continue;
    }

    if (line.length <= 82 && line.endsWith(":")) {
      blocks.push({ type: "heading", level: 3, text: cleanText(line.slice(0, -1)) });
      index += 1;
      continue;
    }

    const paragraph = [line];
    index += 1;
    while (index < lines.length) {
      const next = lines[index].trim();
      if (!next || isStructure(next) || (next.length <= 82 && next.endsWith(":"))) break;
      paragraph.push(next);
      index += 1;
    }
    blocks.push({ type: "paragraph", text: paragraph.join(" ") });
  }

  return blocks;
}

export default function FinwizAnswer({ answer }: { answer: string }) {
  const blocks = parseAnswer(answer);

  return <div className={styles.answerText}>
    {blocks.map((block, index) => {
      if (block.type === "heading") {
        return block.level <= 2
          ? <h3 key={index}>{formatInline(block.text)}</h3>
          : <h4 key={index}>{formatInline(block.text)}</h4>;
      }
      if (block.type === "paragraph") {
        return <p key={index}>{formatInline(block.text)}</p>;
      }
      if (block.type === "bullets") {
        return <ul key={index}>{block.items.map((item, itemIndex) => <li key={itemIndex}>{formatInline(item)}</li>)}</ul>;
      }
      if (block.type === "numbers") {
        return <ol key={index}>{block.items.map((item, itemIndex) => <li key={itemIndex}>{formatInline(item)}</li>)}</ol>;
      }
      if (block.type === "formula") {
        return <div className={styles.formulaBlock} key={index}>{block.text}</div>;
      }
      if (block.type === "quote") {
        return <blockquote key={index}>{formatInline(block.text)}</blockquote>;
      }
      return <hr key={index} />;
    })}
  </div>;
}
