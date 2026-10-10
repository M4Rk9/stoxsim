import Link from "next/link";
import type { Metadata } from "next";
import LegalDocument from "../components/LegalDocument";
import { createPublicMetadata } from "../seo";

export const metadata: Metadata = createPublicMetadata(
  "Delete your StoxSim account",
  "How to request permanent deletion of your StoxSim account and associated data.",
  "/delete-account",
);

export default function DeleteAccountPage() {
  return (
    <LegalDocument
      title="Delete your StoxSim account"
      summary="You can request permanent account deletion on the website, including when you no longer have the Android app."
      effectiveDate="10 October 2026"
    >
      <section>
        <h2>Delete from account settings</h2>
        <ol>
          <li>Sign in to your StoxSim account.</li>
          <li>Open <Link href="/settings">Settings</Link> and find Delete account.</li>
          <li>Enter your password and confirm permanent deletion.</li>
        </ol>
        <p>You can export your account data in Settings before deleting it.</p>
      </section>
      <section>
        <h2>If you cannot sign in</h2>
        <p>
          Use <Link href="/forgot-password">password recovery</Link>, or email{" "}
          <a href="mailto:support.stoxsim@gmail.com?subject=StoxSim%20account%20deletion%20request">
            support.stoxsim@gmail.com
          </a>{" "}
          from your registered email address to request deletion. We may need to verify your identity.
          Never send your password or verification codes by email.
        </p>
      </section>
      <section>
        <h2>What is deleted</h2>
        <p>
          Deletion removes your account and associated simulator and learning data, including
          portfolios, watchlists, simulated orders and trades, saved scenarios, reports,
          progression and product activity. Account-linked campus data is removed and campus
          moderation records have account references cleared.
        </p>
        <p>
          Limited security and backup records may remain for the period needed for security
          or legal obligations, as explained in the <Link href="/privacy">Privacy Notice</Link>.
          Deleting the app from your phone does not delete your account.
        </p>
      </section>
    </LegalDocument>
  );
}
