"use client";

import { FormEvent, useEffect, useState } from "react";
import { ApiError, campus, session } from "./client";
import styles from "./campus.module.css";

interface Institution { id: string; name: string; emailDomain: string; suspended: boolean }
interface Profile { platformAdmin: boolean; emailVerified: boolean; membership?: { institutionId: string; institutionName: string; role: string } }
interface JoinRequest { id: string; institutionId: string; institutionName: string; userId: string; displayName: string; email: string; note: string; status: string; reviewNote?: string }
interface Competition { id: string; institutionId: string; title: string; startsAt: string; endsAt: string; status: string; capacity: number; participants: number; enrolled: boolean; withdrawn: boolean; cancellationNote?: string }
interface Workspace { institution: Institution; viewerRole: string; competitions: Competition[] }
interface Management { members: { userId: string; displayName: string; role: string }[]; requests: JoinRequest[]; audit: { action: string; actor: string; target?: string; competition?: string; createdAt: string }[] }
interface Board { competition: Competition; standings: { rank: number; displayName: string; returnPercent: number; dataStatus: string; joinedAt: string; valuedAt: string; currentUser: boolean }[]; yourBaselineValue?: number; yourLatestValue?: number; refreshUnavailable: boolean; comparisonNote: string }
const time = (value: string) => new Date(value).toLocaleString();
const money = (value?: number) => value == null ? "—" : new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(value);

export default function CampusPage() {
  const [profile, setProfile] = useState<Profile | null>(null);
  const [institutions, setInstitutions] = useState<Institution[]>([]);
  const [latest, setLatest] = useState<JoinRequest | null>(null);
  const [selected, setSelected] = useState<Institution | null>(null);
  const [workspace, setWorkspace] = useState<Workspace | null>(null);
  const [management, setManagement] = useState<Management | null>(null);
  const [board, setBoard] = useState<Board | null>(null);
  const [query, setQuery] = useState("");
  const [note, setNote] = useState("");
  const [consent, setConsent] = useState(false);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [signIn, setSignIn] = useState(false);
  const manager = workspace?.viewerRole === "ADMIN" || workspace?.viewerRole === "ORGANIZER";
  const base = selected ? `/institutions/${selected.id}` : "";

  function clearWorkspace() { setWorkspace(null); setManagement(null); setBoard(null); setConsent(false); }
  function failure(cause: unknown) {
    setError(cause instanceof Error ? cause.message : "Campus could not be loaded.");
    if (cause instanceof ApiError && [401, 403, 404].includes(cause.status)) {
      clearWorkspace();
      if (cause.status === 401) { setProfile(null); setLatest(null); setSignIn(true); }
    }
  }
  async function openInstitution(item: Institution, current: Profile) {
    clearWorkspace(); setSelected(item); setNote("");
    if (!current.platformAdmin && current.membership?.institutionId !== item.id) return;
    const next = await campus<Workspace>(`/institutions/${item.id}`);
    const manage = ["ADMIN", "ORGANIZER"].includes(next.viewerRole)
      ? await campus<Management>(`/institutions/${item.id}/manage`) : null;
    setWorkspace(next); setSelected(next.institution); setManagement(manage);
  }
  async function load() {
    clearWorkspace(); setSelected(null);
    const [current, directory, request] = await Promise.all([campus<Profile>(), campus<Institution[]>(`/institutions?q=${encodeURIComponent(query)}`), campus<JoinRequest | null>("/membership-request")]);
    setProfile(current); setInstitutions(directory); setLatest(request); setSignIn(false);
    if (current.membership) await openInstitution({ id: current.membership.institutionId, name: current.membership.institutionName, emailDomain: "", suspended: false }, current);
  }
  useEffect(() => { void load().catch(failure).finally(() => setBusy(false)); /* initial authenticated load */
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  async function act(action: () => Promise<void>, message = "") {
    setBusy(true); setError(""); setNotice("");
    try { await action(); setNotice(message); } catch (cause) { failure(cause); } finally { setBusy(false); }
  }
  async function reloadSelected(boardId?: string) {
    const current = await campus<Profile>(); setProfile(current);
    if (selected) await openInstitution(selected, current);
    if (boardId) setBoard(await campus<Board>(`${base}/competitions/${boardId}`));
  }
  function reasonAction(prompt: string, path: string, extra: object = {}, after?: () => Promise<void>) {
    const reason = window.prompt(prompt);
    if (!reason?.trim()) return;
    void act(async () => { await campus(path, { ...extra, note: reason }); await (after ? after() : reloadSelected()); }, "Change saved.");
  }
  function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const data = new FormData(event.currentTarget); const form = event.currentTarget;
    void act(async () => {
      const start = String(data.get("start") ?? "");
      const item = await campus<Competition>(`${base}/competitions`, { title: data.get("title"), startsAt: start ? new Date(start).toISOString() : null, endsAt: new Date(String(data.get("end"))).toISOString(), capacity: Number(data.get("capacity")) });
      form.reset(); await reloadSelected(item.id);
    }, "Competition created. Members can enroll when it opens.");
  }

  return <main id="main-content" tabIndex={-1} className={styles.shell} aria-busy={busy}>
    <nav className={styles.nav}><a href="/">StoxSim</a><a href="/competitions">All competitions</a></nav>
    <header className={styles.hero}><span>LEARN WITH YOUR CAMPUS</span><h1>Campus competitions</h1><p>Join your verified institution, practice with your standard ₹5 lakh India portfolio, and compare your progress with fellow learners.</p></header>
    {error && <div role="alert" className={styles.alert}>{error} <button disabled={busy} onClick={() => void act(load)}>Reload campus</button></div>}
    {signIn && <p><a href="/">Sign in to StoxSim</a> to access your campus.</p>}
    {notice && <p role="status" className={styles.notice}>{notice}</p>}
    {busy && <p role="status">Loading campus…</p>}
    {profile && <>
      {!profile.emailVerified && <p className={styles.notice}>Verify your StoxSim email before requesting membership or enrolling.</p>}
      {profile.membership && <section className={styles.card}><h2>Your membership</h2><p>{profile.membership.institutionName} · {profile.membership.role}</p><div className={styles.actions}>
        <button disabled={busy} onClick={() => void act(load)}>Open my campus</button>
        <button className={styles.secondary} disabled={busy} onClick={() => reasonAction("Leaving withdraws you from every campus competition. The last organizer must assign a replacement first. Enter a reason to leave:", `/institutions/${profile.membership!.institutionId}/members/${session()?.user.id}/remove`, {}, load)}>Leave institution</button>
      </div></section>}
      {latest && !profile.membership && <section className={styles.card}><h2>Membership request</h2><p>{latest.institutionName} · {latest.status}</p>{latest.reviewNote && <p>{latest.reviewNote}</p>}
        {latest.status === "PENDING" && <><p>An organizer will check your affiliation. You can keep using StoxSim while you wait.</p><button disabled={busy} onClick={() => void act(async () => { await campus(`/membership-requests/${latest.id}/cancel`, null); setLatest(await campus("/membership-request")); }, "Request cancelled.")}>Cancel request</button></>}
      </section>}
      <section className={styles.card}><h2>Find an institution</h2><form className={styles.search} onSubmit={event => { event.preventDefault(); void act(async () => setInstitutions(await campus(`/institutions?q=${encodeURIComponent(query)}`))); }}>
        <label>Institution name<input value={query} onChange={event => setQuery(event.target.value)} maxLength={160} placeholder="Search verified institutions" /></label><button disabled={busy}>Search</button>
      </form><p>Showing up to 50 matching institutions. <a href="/competitions">Request verification for a new institution</a>.</p>
      <div className={styles.grid}>{institutions.map(item => <button key={item.id} className={styles.institution} disabled={busy} aria-pressed={selected?.id === item.id} onClick={() => void act(() => openInstitution(item, profile))}><strong>{item.name}</strong><span>{item.emailDomain}{item.suspended ? " · Suspended" : " · Verified"}</span></button>)}</div>
      {!institutions.length && <p>No matching verified institutions.</p>}</section>
      {selected && !workspace && !profile.membership && !profile.platformAdmin && <section className={styles.card}><h2>Request to join {selected.name}</h2><p>An organizer reviews your name, email and affiliation note. Your email domain alone does not prove affiliation. Do not include identity documents or other sensitive details.</p>
        <form onSubmit={event => { event.preventDefault(); void act(async () => { setLatest(await campus(`${base}/membership-requests`, { note })); setNote(""); }, "Membership request sent."); }}>
          <label>Affiliation note<textarea required maxLength={300} value={note} onChange={event => setNote(event.target.value)} placeholder="Course, department or campus club" /></label>
          <button disabled={busy || !profile.emailVerified || latest?.status === "PENDING"}>Request membership</button>
        </form></section>}
      {workspace && <>
        <section className={styles.card}><div className={styles.heading}><div><span>{workspace.viewerRole}</span><h2>{workspace.institution.name}</h2></div><button disabled={busy} onClick={() => void act(() => reloadSelected())}>Refresh campus</button></div>
          {workspace.institution.suspended && <p className={styles.notice}>This institution is suspended. New requests, competitions, enrollment and score refreshes are paused. Contact StoxSim support.</p>}
          <h3>Competitions</h3><div className={styles.grid}>{workspace.competitions.map(item => <button className={styles.institution} disabled={busy} key={item.id} onClick={() => void act(async () => { setBoard(null); setConsent(false); setBoard(await campus(`${base}/competitions/${item.id}`)); })}><span>{item.status}{item.enrolled ? " · Joined" : item.withdrawn ? " · Withdrawn" : ""}</span><strong>{item.title}</strong><span>{item.participants}/{item.capacity} learners</span><span>{time(item.startsAt)} – {time(item.endsAt)}</span></button>)}</div>
          {!workspace.competitions.length && <p>No competitions yet. Your organizer can create one.</p>}
          <p className={styles.muted}>Latest 50 competitions. Times use your device’s time zone. Joining a campus competition does not enroll you in the global leaderboard.</p>
        </section>
        {board && <section className={styles.card} aria-label="Campus standings"><div className={styles.heading}><div><span>{board.competition.status}</span><h2>{board.competition.title}</h2></div><button disabled={busy} onClick={() => void act(async () => { const id = board.competition.id; setBoard(null); setBoard(await campus(`${base}/competitions/${id}/refresh`, null)); })}>Refresh my score</button></div>
          <p>{time(board.competition.startsAt)} – {time(board.competition.endsAt)} · {board.competition.participants}/{board.competition.capacity} learners</p>
          {board.competition.cancellationNote && <p className={styles.notice}>Cancelled: {board.competition.cancellationNote}</p>}
          {board.refreshUnavailable && <p role="status" className={styles.notice}>Prices are unavailable. Your last usable score is shown; try refreshing later.</p>}
          {board.competition.enrolled ? <div className={styles.metrics}><p>Your entry baseline<strong>{money(board.yourBaselineValue)}</strong></p><p>Your latest value<strong>{money(board.yourLatestValue)}</strong></p><button className={styles.secondary} disabled={busy} onClick={() => { if (window.confirm("Withdraw permanently from this competition? You cannot re-enroll or reset your baseline.")) void act(async () => { await campus(`${base}/competitions/${board.competition.id}/withdraw`, null); await reloadSelected(board.competition.id); }, "Withdrawn. Re-enrollment is disabled for this competition."); }}>Withdraw</button></div>
          : board.competition.withdrawn ? <p className={styles.notice}>You withdrew from this competition. Re-enrollment is disabled.</p>
          : board.competition.status === "OPEN" && profile.membership?.institutionId === selected?.id && <div className={styles.optin}><label className={styles.check}><input type="checkbox" checked={consent} onChange={event => setConsent(event.target.checked)} />Share my display name, entry-relative return, entry time and valuation freshness with campus members and platform admins.</label><p>Your current standard India portfolio sets your baseline. Other members cannot see your balance. Paid custom accounts cannot enter.</p><button disabled={busy || !consent || !profile.emailVerified || workspace.institution.suspended || board.competition.participants >= board.competition.capacity} onClick={() => void act(async () => setBoard(await campus(`${base}/competitions/${board.competition.id}/enroll`, null)), "You joined this campus competition.")}>{board.competition.participants >= board.competition.capacity ? "Competition full" : "Join campus competition"}</button></div>}
          <div className={styles.tableWrap}><table aria-label="Campus leaderboard"><thead><tr><th>Rank</th><th>Learner</th><th>Return since entry</th><th>Entered</th><th>Last valued</th><th>Pricing</th></tr></thead><tbody>{board.standings.map((entry, index) => <tr key={index} className={entry.currentUser ? styles.you : undefined}><td>#{entry.rank}</td><td>{entry.displayName}{entry.currentUser && " (you)"}</td><td>{entry.returnPercent > 0 ? "+" : ""}{entry.returnPercent.toFixed(2)}%</td><td>{time(entry.joinedAt)}</td><td>{time(entry.valuedAt)}</td><td>{entry.dataStatus}</td></tr>)}</tbody></table></div>
          {!board.standings.length && <p>No enrolled learners yet.</p>}<p className={styles.muted}>{board.comparisonNote}</p>
          {manager && ["OPEN", "SCHEDULED"].includes(board.competition.status) && <button className={styles.secondary} disabled={busy} onClick={() => reasonAction("Cancel this competition for all members? Enter a reason visible on its board:", `${base}/competitions/${board.competition.id}/cancel`, {}, () => reloadSelected(board.competition.id))}>Cancel competition</button>}
        </section>}
        {manager && management && <>
          <section className={styles.card}><h2>Organizer controls</h2><p>Confirm affiliation before approving a member. Enrollment remains the learner’s choice. Limit: 200 members and ten upcoming or active competitions.</p><h3>Membership queue</h3>
            {management.requests.map(request => <article className={styles.row} key={request.id}><div><strong>{request.displayName}</strong><p>{request.email}</p><p>{request.note}</p></div><div className={styles.actions}><button disabled={busy || workspace.institution.suspended} onClick={() => reasonAction("Confirm you checked this learner’s affiliation. Enter a review note visible to them:", `${base}/membership-requests/${request.id}/review`, { approve: true })}>Approve {request.displayName}</button><button className={styles.secondary} disabled={busy} onClick={() => reasonAction("Enter a rejection reason visible to this learner:", `${base}/membership-requests/${request.id}/review`, { approve: false })}>Reject {request.displayName}</button></div></article>)}
            {!management.requests.length && <p>No pending membership requests.</p>}
            <details><summary>Members ({management.members.length})</summary>{management.members.map(member => <article className={styles.row} key={member.userId}><div><strong>{member.displayName}</strong><p>{member.role}</p></div><div className={styles.actions}><button disabled={busy || workspace.institution.suspended} onClick={() => { const role = member.role === "ORGANIZER" ? "MEMBER" : "ORGANIZER"; if (window.confirm(`Change ${member.displayName} to ${role}? Organizers can approve members and manage competitions.`)) void act(async () => { await campus(`${base}/members/${member.userId}/role`, { role }); await reloadSelected(); }, "Campus role updated."); }}>{member.role === "ORGANIZER" ? "Make member" : "Make organizer"}</button><button className={styles.secondary} disabled={busy} onClick={() => reasonAction(`Remove ${member.displayName} and withdraw their competition entries? Enter a reason:`, `${base}/members/${member.userId}/remove`)}>Remove</button></div></article>)}</details>
          </section>
          <section className={styles.card}><h2>Create a competition</h2><p>Choose a duration from one hour to 90 days. Dates, title and capacity are fixed after creation; you can cancel a competition before it ends.</p><form onSubmit={create}><fieldset disabled={busy || workspace.institution.suspended}><label>Competition title<input name="title" required minLength={3} maxLength={100} /></label><div className={styles.grid}><label>Starts at (optional)<input name="start" type="datetime-local" /><small>Leave blank to open immediately.</small></label><label>Ends at<input name="end" type="datetime-local" required /></label><label>Participant limit<input name="capacity" type="number" min={2} max={200} defaultValue={50} required /></label></div><button>Create competition</button></fieldset></form></section>
          <section className={styles.card}><details><summary>Recent campus activity</summary><p>Latest 50 actions. Names reflect current accounts.</p>{management.audit.map((entry, index) => <p key={index}><strong>{entry.action.replaceAll("_", " ").toLowerCase()}</strong> · {entry.actor}{entry.target ? ` → ${entry.target}` : ""}{entry.competition ? ` · ${entry.competition}` : ""} · {time(entry.createdAt)}</p>)}</details></section>
        </>}
        {profile.platformAdmin && <section className={styles.card}><h2>Platform admin controls</h2><p>Restore organizer access only after checking the account’s connection to this institution.</p><form onSubmit={event => { event.preventDefault(); const data = new FormData(event.currentTarget); void act(async () => { await campus(`${base}/organizer-recovery`, { email: data.get("email"), note: data.get("reason") }); await reloadSelected(); }, "Organizer access restored."); }}><label>Organizer email<input name="email" type="email" required maxLength={320} /></label><label>Recovery reason<input name="reason" required maxLength={500} /></label><button disabled={busy}>Assign verified organizer</button></form><hr /><button className={styles.secondary} disabled={busy} onClick={() => reasonAction("Enter an institution moderation reason:", `${base}/suspension`, { suspended: !workspace.institution.suspended })}>{workspace.institution.suspended ? "Restore institution" : "Suspend institution"}</button></section>}
      </>}
    </>}
    <footer className={styles.muted}>Educational paper trading. Competition returns are not investment advice. <a href="/privacy">Privacy notice</a></footer>
  </main>;
}
