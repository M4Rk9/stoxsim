# StoxSim product foundations

Use the supplied logo mark with **StoxSim** as one neutral wordmark. Light surfaces use `--ink`; dark surfaces use the dark `--ink` variant. The mark retains its own artwork. Do not colour just “Sim”, substitute initials, or invent a second mark. `Brand.tsx` is the web implementation; native brand text uses the same neutral foreground and existing Android assets.

| Role | Light | Dark |
| --- | --- | --- |
| Primary text | #17211d | #eef3ef |
| Secondary text | #596a62 | #a9b8ae |
| Accent | #087a4b | #5bd6a2 |
| Accent foreground | #ffffff | #0c2418 |
| Page | #f4f6f1 | #101513 |
| Panel | #ffffff | #18201c |
| Border | #dfe5de | #303e36 |

Use accent for actions, selected states and meaningful gains. Losses retain red and textual signs/status, so colour is not the sole indicator. Tables and charts use real observations; absent data is a labelled gap, not a decorative substitute.

System sans-serif stack, native Compose typography, 14–16px reading text; compact metadata may use 12–13px. Heading scale 22/26/32/38/52px by hierarchy and width. Shared spacing tokens: 4, 8, 12, 16, 24, 32px. Radius tokens: 10px controls, 16px cards, 22px major containers. Minimum interactive height 44px web and Material native touch behavior. Visible keyboard focus and reduced-motion preferences remain supported.

Web and Android share five main destinations: Home, Markets, Learn, Compete and Account. The journal is one tap away from Home and Learn; it sits in the Learn area so mobile navigation keeps readable labels and 44px targets.

The home hub groups the research loop with a review-due card and persistent Finwiz/Scenario/Learn/Competition shortcuts. Journal forms progressively disclose saved research and actual linked-order outcomes. Mobile stacks panels without hiding required fields. Finwiz retains its focused chat layout without a bottom navigation overlay.

This is incremental adoption. Existing domain pages keep their working components; remaining page-specific hard-coded styling is tracked in the audit and subsequent milestones.
