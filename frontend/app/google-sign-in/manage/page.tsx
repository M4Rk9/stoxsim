import { headers } from "next/headers";
import GoogleSignIn from "../GoogleSignIn";

export const metadata = { title: "Manage Google sign-in | StoxSim", robots: { index: false, follow: false } };
export default async function Page() {
  const nonce = (await headers()).get("x-nonce") ?? undefined;
  return <GoogleSignIn scriptNonce={nonce} manage />;
}
