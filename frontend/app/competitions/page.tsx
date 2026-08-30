"use client";

import Image from "next/image";
import { FormEvent, useEffect, useState } from "react";
import styles from "./competitions.module.css";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

interface StoredSession {
  accessToken: string;
  expiresInSeconds: number;
  user: { id: string; email: string; displayName: string };
}

interface Season {
  code: string;
  title: string;
  startsAt: string;
  endsAt: string;
  open: boolean;
  scoringVersion: string;
  marketRegion: string;
  currency: string;
  standardStartingCapital: number;
}

interface Standing {
  rank: number;
  displayName: string;
  returnPercent: number;
  dataStatus: string;
  joinedAt: string;
  valuedAt: string;
  currentUser: boolean;
}

interface Board {
  season: Season;
  enrolled: boolean;
  participantCount: number;
  yourRank?: number;
  yourBaselineValue?: number;
  yourLatestValue?: number;
  standings: Standing[];
  comparisonNote: string;
  disclaimer: string;
}

interface LeagueSummary {
  id: string;
  name: string;
  seasonCode: string;
  ownerDisplayName: string;
  owner: boolean;
  memberCount: number;
  maxMembers: number;
  createdAt: string;
}

interface LeagueDetail {
  league: LeagueSummary;
  season: Season;
  standings: Standing[];
  comparisonNote: string;
  disclaimer: string;
}

interface LeagueCreated {
  league: LeagueDetail;
  inviteCode: string;
  inviteNote: string;
}

interface LeagueInvite { inviteCode: string; inviteNote: string }

interface CampusVerification {
  id: string;
  institutionName: string;
  emailDomain: string;
  websiteUrl?: string;
  status: "PENDING" | "APPROVED" | "REJECTED";
  requesterDisplayName: string;
  requesterEmail: string;
  submittedAt: string;
  reviewedAt?: string;
  reviewNote?: string;
}

interface CampusProfile {
  version: "campus-verification-v1";
  platformAdmin: boolean;
  emailVerified: boolean;
  membership?: {
    institutionId: string;
    institutionName: string;
    emailDomain: string;
    websiteUrl?: string;
    role: "ORGANIZER" | "MEMBER";
    institutionVerifiedAt: string;
    joinedAt: string;
  };
  latestVerificationRequest?: CampusVerification;
  notice: string;
}

class ApiError extends Error {
  constructor(message: string, readonly status: number) { super(message); }
}

function readSession(): StoredSession | null {
  try {
    const value = window.sessionStorage.getItem("stoxsim-session");
    return value ? JSON.parse(value) as StoredSession : null;
  } catch { return null; }
}

function storeSession(session: StoredSession) {
  window.sessionStorage.setItem("stoxsim-session", JSON.stringify(session));
}

let refreshInFlight: Promise<StoredSession> | null = null;

async function request<T>(path: string, options: RequestInit = {}, token?: string): Promise<T> {
  const response = await fetch(`${API_URL}${path}`, {
    credentials: "include",
    ...options,
    headers: {
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });
  if (!response.ok) {
    const payload = await response.json().catch(() => null);
    throw new ApiError(payload?.message ?? `Request failed with status ${response.status}`, response.status);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

function refreshSession(): Promise<StoredSession> {
  if (refreshInFlight) return refreshInFlight;
  refreshInFlight = request<StoredSession>("/api/v1/auth/refresh", { method: "POST" })
    .then((session) => {
      storeSession(session);
      return session;
    })
    .finally(() => { refreshInFlight = null; });
  return refreshInFlight;
}

async function authorized<T>(path: string, options: RequestInit = {}): Promise<T> {
  const session = readSession() ?? await refreshSession();
  try { return await request<T>(path, options, session.accessToken); }
  catch (cause) {
    if (!(cause instanceof ApiError) || cause.status !== 401) throw cause;
    const refreshed = await refreshSession();
    return request<T>(path, options, refreshed.accessToken);
  }
}

function money(value: number | undefined) {
  if (value == null) return "—";
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(value);
}

function percent(value: number) {
  return `${value > 0 ? "+" : ""}${value.toFixed(2)}%`;
}

function timestamp(value: string) {
  return new Intl.DateTimeFormat("en-IN", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function Standings({ standings }: { standings: Standing[] }) {
  if (standings.length === 0) return <p className={styles.empty}>No standings yet. Be the first learner to opt in.</p>;
  return <div className={styles.tableWrap}>
    <table>
      <thead><tr><th>Rank</th><th>Learner</th><th>Return since entry</th><th>Pricing</th><th>Last valued</th></tr></thead>
      <tbody>{standings.map((entry) => <tr className={entry.currentUser ? styles.you : undefined} key={`${entry.rank}-${entry.joinedAt}-${entry.displayName}`}>
        <td>#{entry.rank}</td>
        <td><strong>{entry.displayName}</strong>{entry.currentUser && <small>You</small>}</td>
        <td className={entry.returnPercent >= 0 ? styles.positive : styles.negative}>{percent(entry.returnPercent)}</td>
        <td><span className={styles.status}>{entry.dataStatus}</span></td>
        <td>{timestamp(entry.valuedAt)}</td>
      </tr>)}</tbody>
    </table>
  </div>;
}

export default function CompetitionsPage() {
  const [board, setBoard] = useState<Board | null>(null);
  const [leagues, setLeagues] = useState<LeagueSummary[]>([]);
  const [selected, setSelected] = useState<LeagueDetail | null>(null);
  const [name, setName] = useState("");
  const [joinCode, setJoinCode] = useState("");
  const [invite, setInvite] = useState<LeagueInvite | null>(null);
  const [campus, setCampus] = useState<CampusProfile | null>(null);
  const [campusQueue, setCampusQueue] = useState<CampusVerification[]>([]);
  const [institutionName, setInstitutionName] = useState("");
  const [emailDomain, setEmailDomain] = useState("");
  const [websiteUrl, setWebsiteUrl] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState("");

  async function loadLeagues() {
    setLeagues(await authorized<LeagueSummary[]>("/api/v1/leagues"));
  }

  useEffect(() => {
    let active = true;
    Promise.all([
      authorized<Board>("/api/v1/competitions/current"),
      authorized<LeagueSummary[]>("/api/v1/leagues"),
      authorized<CampusProfile>("/api/v1/campus"),
    ]).then(([current, leagueMemberships, campusProfile]) => {
      if (active) {
        setBoard(current);
        setLeagues(leagueMemberships);
        setCampus(campusProfile);
      }
      if (campusProfile.platformAdmin) {
        void authorized<CampusVerification[]>("/api/v1/campus/admin/verification-requests")
          .then((queue) => { if (active) setCampusQueue(queue); })
          .catch((cause) => {
            if (active) setError(cause instanceof Error
              ? cause.message
              : "The campus moderation queue could not be loaded.");
          });
      }
    }).catch((cause) => {
      if (active) setError(cause instanceof Error ? cause.message : "Competitions could not be loaded.");
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  async function act(label: string, action: () => Promise<void>) {
    setBusy(label); setError("");
    try { await action(); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "The request could not be completed."); }
    finally { setBusy(""); }
  }

  function enroll() {
    void act("enroll", async () => {
      setBoard(await authorized<Board>("/api/v1/competitions/current/enroll", { method: "POST" }));
    });
  }

  function createLeague(event: FormEvent) {
    event.preventDefault();
    void act("create", async () => {
      const created = await authorized<LeagueCreated>("/api/v1/leagues", {
        method: "POST", body: JSON.stringify({ name }),
      });
      setSelected(created.league);
      setInvite({ inviteCode: created.inviteCode, inviteNote: created.inviteNote });
      setName("");
      await loadLeagues();
    });
  }

  function joinLeague(event: FormEvent) {
    event.preventDefault();
    void act("join", async () => {
      const detail = await authorized<LeagueDetail>("/api/v1/leagues/join", {
        method: "POST", body: JSON.stringify({ inviteCode: joinCode }),
      });
      setSelected(detail); setJoinCode(""); setInvite(null);
      await loadLeagues();
    });
  }

  function requestCampusVerification(event: FormEvent) {
    event.preventDefault();
    void act("campus-request", async () => {
      const submitted = await authorized<CampusVerification>(
        "/api/v1/campus/verification-requests",
        {
          method: "POST",
          body: JSON.stringify({ institutionName, emailDomain, websiteUrl }),
        },
      );
      setCampus((current) => current
        ? { ...current, latestVerificationRequest: submitted }
        : current);
      setInstitutionName("");
      setEmailDomain("");
      setWebsiteUrl("");
    });
  }

  function reviewCampus(requestId: string, decision: "approve" | "reject") {
    const note = window.prompt(decision === "approve"
      ? "Optional approval note visible to the requester"
      : "Reason for rejection (required)");
    if (note === null || (decision === "reject" && !note.trim())) return;
    void act(`campus-${decision}-${requestId}`, async () => {
      await authorized<CampusVerification>(
        `/api/v1/campus/admin/verification-requests/${requestId}/${decision}`,
        { method: "POST", body: JSON.stringify({ note }) },
      );
      setCampusQueue((queue) => queue.filter((item) => item.id !== requestId));
    });
  }

  function openLeague(id: string) {
    void act(`open-${id}`, async () => {
      setSelected(await authorized<LeagueDetail>(`/api/v1/leagues/${id}`));
      setInvite(null);
    });
  }

  function rotateInvite() {
    if (!selected) return;
    void act("rotate", async () => setInvite(await authorized<LeagueInvite>(
      `/api/v1/leagues/${selected.league.id}/invite/rotate`, { method: "POST" },
    )));
  }

  function leaveOrDelete() {
    if (!selected) return;
    const owner = selected.league.owner;
    if (!window.confirm(owner ? "Delete this league for every member?" : "Leave this league?")) return;
    void act("remove", async () => {
      await authorized<void>(`/api/v1/leagues/${selected.league.id}${owner ? "" : "/leave"}`, {
        method: owner ? "DELETE" : "POST",
      });
      setSelected(null); setInvite(null); await loadLeagues();
    });
  }

  return <main className={styles.shell}>
    <header className={styles.header}>
      <a className={styles.brand} href="/" aria-label="StoxSim dashboard"><Image src="/stoxsim-logo.png" alt="" width={42} height={42} priority /><span>Stox<span>Sim</span></span></a>
      <a className={styles.back} href="/">Back to dashboard</a>
    </header>

    <section className={styles.heading}>
      <span>STANDARD ₹5 LAKH PORTFOLIO</span>
      <h1>Learning competitions</h1>
      <p>Compare percentage change after entry on equal standard accounts, or learn with a private group. Competition results are educational—not investment advice.</p>
    </section>

    {loading && <div className={styles.loading}>Loading competitions…</div>}
    {error && <div className={styles.error} role="alert">{error}</div>}

    {board && <>
      <section className={styles.seasonCard}>
        <div><span>CURRENT SEASON</span><h2>{board.season.title}</h2><p>{timestamp(board.season.startsAt)} – {timestamp(board.season.endsAt)}</p></div>
        <div className={styles.seasonFacts}><span>{board.participantCount} learners</span><span>{board.season.scoringVersion}</span></div>
      </section>

      {!board.enrolled ? <section className={styles.enrollCard}>
        <div><h2>Opt in to this season</h2><p>Your current standard India account value becomes your private entry baseline. Your display name, entry-relative return, join time and valuation freshness become visible in the global season standings. Joining earlier or later is always visible.</p></div>
        <button type="button" onClick={enroll} disabled={busy === "enroll" || !board.season.open}>{busy === "enroll" ? "Joining…" : "Join standard leaderboard"}</button>
      </section> : <section className={styles.metrics} aria-label="Your competition position">
        <article><span>Your rank</span><strong>{board.yourRank ? `#${board.yourRank}` : "—"}</strong></article>
        <article><span>Entry baseline</span><strong>{money(board.yourBaselineValue)}</strong></article>
        <article><span>Latest value</span><strong>{money(board.yourLatestValue)}</strong></article>
      </section>}

      <section className={styles.board}>
        <div className={styles.sectionHeading}><div><span>GLOBAL SEASON</span><h2>Standard leaderboard</h2></div><small>Top 50</small></div>
        <Standings standings={board.standings} />
        <p className={styles.note}>{board.comparisonNote}</p>
      </section>

      {campus && <section className={styles.campusArea} aria-labelledby="campus-title">
        <div className={styles.sectionHeading}>
          <div><span>VERIFIED INSTITUTIONS</span><h2 id="campus-title">Campus verification</h2></div>
          <small>{campus.version}</small>
        </div>
        {campus.membership ? <div className={styles.campusVerified}>
          <div><strong>{campus.membership.institutionName}</strong><span>{campus.membership.emailDomain}</span></div>
          <span>{campus.membership.role} · VERIFIED</span>
        </div> : campus.latestVerificationRequest?.status === "PENDING" ? <div className={styles.campusPending} role="status">
          <strong>Verification pending</strong>
          <span>{campus.latestVerificationRequest.institutionName} · submitted {timestamp(campus.latestVerificationRequest.submittedAt)}</span>
        </div> : <>
          {campus.latestVerificationRequest?.status === "REJECTED" && <div className={styles.campusRejected} role="status">
            <strong>Previous request needs changes</strong>
            <span>{campus.latestVerificationRequest.reviewNote ?? "Review the institution details and submit again."}</span>
          </div>}
          <form className={styles.campusForm} onSubmit={requestCampusVerification}>
            <label>Institution name<input required minLength={3} maxLength={160} value={institutionName} onChange={(event) => setInstitutionName(event.target.value)} placeholder="Birla Institute of Technology, Mesra" disabled={!campus.emailVerified} /></label>
            <label>Official email domain<input required maxLength={190} value={emailDomain} onChange={(event) => setEmailDomain(event.target.value)} placeholder="bitmesra.ac.in" disabled={!campus.emailVerified} /></label>
            <label>Official website<input type="url" pattern="https://.*" maxLength={300} value={websiteUrl} onChange={(event) => setWebsiteUrl(event.target.value)} placeholder="https://www.bitmesra.ac.in" disabled={!campus.emailVerified} /></label>
            <button disabled={!campus.emailVerified || busy === "campus-request"}>{busy === "campus-request" ? "Submitting…" : "Request verification"}</button>
          </form>
          {!campus.emailVerified && <p className={styles.leagueConsent}>Verify your StoxSim email before submitting an institution request.</p>}
        </>}
        <p className={styles.note}>{campus.notice}</p>

        {campus.platformAdmin && <div className={styles.moderation}>
          <div className={styles.sectionHeading}><div><span>PLATFORM ADMIN</span><h3>Verification queue</h3></div><small>{campusQueue.length} pending</small></div>
          {campusQueue.map((item) => <article key={item.id}>
            <div><strong>{item.institutionName}</strong><span>{item.emailDomain} · {item.requesterEmail}</span>{item.websiteUrl && <a href={item.websiteUrl} target="_blank" rel="noreferrer">Review website</a>}</div>
            <div><button type="button" onClick={() => reviewCampus(item.id, "approve")} disabled={busy.startsWith("campus-")}>Approve</button><button type="button" className={styles.danger} onClick={() => reviewCampus(item.id, "reject")} disabled={busy.startsWith("campus-")}>Reject</button></div>
          </article>)}
          {campusQueue.length === 0 && <p className={styles.empty}>No institution requests are awaiting review.</p>}
        </div>}
      </section>}

      <section className={styles.leagueArea}>
        <div className={styles.sectionHeading}><div><span>INVITE-ONLY</span><h2>Private leagues</h2></div><small>Up to 25 learners</small></div>
        {!board.enrolled && <p className={styles.leagueConsent}>Join the standard leaderboard above before creating or joining a private league. This keeps global display-name disclosure an explicit, separate choice.</p>}
        <div className={styles.forms}>
          <form onSubmit={createLeague}><label htmlFor="league-name">Create a league</label><div><input id="league-name" value={name} onChange={(event) => setName(event.target.value)} minLength={3} maxLength={80} required disabled={!board.enrolled} placeholder="Campus finance club" /><button disabled={!board.enrolled || busy === "create"}>{busy === "create" ? "Creating…" : "Create"}</button></div></form>
          <form onSubmit={joinLeague}><label htmlFor="invite-code">Join with a private code</label><div><input id="invite-code" value={joinCode} onChange={(event) => setJoinCode(event.target.value)} minLength={16} maxLength={80} required disabled={!board.enrolled} autoComplete="off" placeholder="STX-…" /><button disabled={!board.enrolled || busy === "join"}>{busy === "join" ? "Joining…" : "Join"}</button></div></form>
        </div>

        <div className={styles.leagueGrid}>{leagues.map((league) => <button type="button" className={styles.leagueCard} key={league.id} onClick={() => openLeague(league.id)} disabled={busy === `open-${league.id}`}>
          <span>{league.owner ? "YOUR LEAGUE" : league.seasonCode}</span><strong>{league.name}</strong><small>{league.memberCount}/{league.maxMembers} learners · Owner: {league.ownerDisplayName}</small>
        </button>)}</div>
        {leagues.length === 0 && <p className={styles.empty}>You have not created or joined a private league yet.</p>}
      </section>

      {selected && <section className={styles.board} aria-label={`${selected.league.name} standings`}>
        <div className={styles.sectionHeading}><div><span>PRIVATE LEAGUE</span><h2>{selected.league.name}</h2></div><div className={styles.leagueActions}>{selected.league.owner && <button type="button" onClick={rotateInvite} disabled={busy === "rotate"}>Rotate invite</button>}<button type="button" className={styles.danger} onClick={leaveOrDelete} disabled={busy === "remove"}>{selected.league.owner ? "Delete league" : "Leave league"}</button></div></div>
        {invite && <div className={styles.invite} role="status"><span>INVITE CODE — SHOWN ONCE</span><code>{invite.inviteCode}</code><p>{invite.inviteNote}</p></div>}
        <Standings standings={selected.standings} />
        <p className={styles.note}>{selected.comparisonNote}</p>
      </section>}

      <p className={styles.disclaimer}>{board.disclaimer}</p>
    </>}
  </main>;
}
