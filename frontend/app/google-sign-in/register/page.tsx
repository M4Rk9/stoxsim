import { headers } from "next/headers";
import GoogleSignIn from "../GoogleSignIn";
export const metadata = { title: "Create account with Google | StoxSim", robots: { index: false, follow: false } };
export default async function Page() {
  const nonce = (await headers()).get("x-nonce") ?? undefined;
  return <GoogleSignIn scriptNonce={nonce} register />;
}
