const socialLinks = [
  { label: "Instagram", href: "https://www.instagram.com/stoxsim/" },
  { label: "X", href: "https://x.com/stoxsim" },
  { label: "Reddit", href: "https://www.reddit.com/r/StoxSimOfficial/" },
  { label: "LinkedIn", href: "https://www.linkedin.com/company/stoxsim" },
] as const;

export default function SocialLinks() {
  return <nav className="socialLinks" aria-label="StoxSim social media">
    {socialLinks.map(({label,href}) => <a key={label} href={href} target="_blank" rel="noopener noreferrer">{label}</a>)}
  </nav>;
}
