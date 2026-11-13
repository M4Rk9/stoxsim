import Image from "next/image";
import styles from "./Brand.module.css";

/** Use the supplied StoxSim mark and one neutral wordmark across both themes. */
export default function Brand() {
  return <a className={styles.brand} href="/" aria-label="StoxSim home">
    <Image src="/stoxsim-logo.png" alt="" width={36} height={36} />
    <span>StoxSim</span>
  </a>;
}
