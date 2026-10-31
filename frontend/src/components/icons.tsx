import type { SVGProps } from 'react'

/** Line icons (24×24, stroke = currentColor). Decorative: hidden from screen readers. */
function Icon({ d, ...props }: SVGProps<SVGSVGElement> & { d: string }) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      className="h-5 w-5"
      {...props}
    >
      <path d={d} />
    </svg>
  )
}

type P = SVGProps<SVGSVGElement>

export const HomeIcon = (p: P) => <Icon {...p} d="M3 11.5 12 4l9 7.5M5.5 9.5V20h13V9.5M10 20v-5.5h4V20" />
export const FolderIcon = (p: P) => (
  <Icon {...p} d="M3 7.5A1.5 1.5 0 0 1 4.5 6H9l2 2h8.5A1.5 1.5 0 0 1 21 9.5v8a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 17.5z" />
)
export const FlaskIcon = (p: P) => <Icon {...p} d="M9 3h6M10 3v6.2L4.6 18.3A1.8 1.8 0 0 0 6.2 21h11.6a1.8 1.8 0 0 0 1.6-2.7L14 9.2V3M7.5 14h9" />
export const PillIcon = (p: P) => (
  <Icon {...p} d="m10.5 20.5-7-7a4.95 4.95 0 0 1 7-7l7 7a4.95 4.95 0 0 1-7 7ZM7 10l7 7" />
)
export const ClipboardIcon = (p: P) => (
  <Icon {...p} d="M9 4h6v3H9zM9 5.5H6.5A1.5 1.5 0 0 0 5 7v12.5A1.5 1.5 0 0 0 6.5 21h11a1.5 1.5 0 0 0 1.5-1.5V7a1.5 1.5 0 0 0-1.5-1.5H15M8.5 12h7M8.5 16h5" />
)
export const UserIcon = (p: P) => <Icon {...p} d="M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-7 9a7 7 0 0 1 14 0" />
export const LogoutIcon = (p: P) => <Icon {...p} d="M15 4h3.5A1.5 1.5 0 0 1 20 5.5v13a1.5 1.5 0 0 1-1.5 1.5H15M10 16l-4-4 4-4M6 12h10" />
export const SearchIcon = (p: P) => <Icon {...p} d="m20 20-4.2-4.2M10.8 17.5a6.7 6.7 0 1 0 0-13.4 6.7 6.7 0 0 0 0 13.4Z" />
export const SunIcon = (p: P) => (
  <Icon {...p} d="M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm0-13v2m0 14v2M3 12h2m14 0h2M5.6 5.6 7 7m10 10 1.4 1.4M5.6 18.4 7 17M17 7l1.4-1.4" />
)
export const MoonIcon = (p: P) => <Icon {...p} d="M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5Z" />
export const UploadIcon = (p: P) => <Icon {...p} d="M12 15V4m0 0L7.5 8.5M12 4l4.5 4.5M4 15v3.5A1.5 1.5 0 0 0 5.5 20h13a1.5 1.5 0 0 0 1.5-1.5V15" />
export const ArrowRightIcon = (p: P) => <Icon {...p} d="M5 12h14m-5-5 5 5-5 5" />
export const AlertIcon = (p: P) => <Icon {...p} d="M12 9v4m0 3.5v.01M10.3 4.2 2.6 17.5A2 2 0 0 0 4.3 20.5h15.4a2 2 0 0 0 1.7-3L13.7 4.2a2 2 0 0 0-3.4 0Z" />
export const CheckIcon = (p: P) => <Icon {...p} d="m5 12.5 4.5 4.5L19 7.5" />
export const PrinterIcon = (p: P) => (
  <Icon {...p} d="M7 9V3.5h10V9M7 17.5H5A1.5 1.5 0 0 1 3.5 16v-5.5A1.5 1.5 0 0 1 5 9h14a1.5 1.5 0 0 1 1.5 1.5V16a1.5 1.5 0 0 1-1.5 1.5h-2M7 14h10v7H7z" />
)
export const DocumentIcon = (p: P) => <Icon {...p} d="M14 3H7.5A1.5 1.5 0 0 0 6 4.5v15A1.5 1.5 0 0 0 7.5 21h9a1.5 1.5 0 0 0 1.5-1.5V7zM14 3v4h4M9 12.5h6M9 16h4" />
export const ScanIcon = (p: P) => <Icon {...p} d="M4 8V5.5A1.5 1.5 0 0 1 5.5 4H8m8 0h2.5A1.5 1.5 0 0 1 20 5.5V8m0 8v2.5a1.5 1.5 0 0 1-1.5 1.5H16m-8 0H5.5A1.5 1.5 0 0 1 4 18.5V16M4 12h16" />
export const TrendIcon = (p: P) => <Icon {...p} d="m3 17 6-6 4 4 8-8m0 0h-5m5 0v5" />
export const ShieldIcon = (p: P) => <Icon {...p} d="M12 3 4.5 6v5.5c0 4.6 3.2 8.3 7.5 9.5 4.3-1.2 7.5-4.9 7.5-9.5V6zm-3 9 2 2 4-4" />
export const ActivityIcon = (p: P) => <Icon {...p} d="M3 12h4l3-8 4 16 3-8h4" />

/** The Medfold mark: a folded page with a pulse line. */
export function Logo({ className = 'h-8 w-8' }: { className?: string }) {
  return (
    <svg aria-hidden="true" viewBox="0 0 32 32" className={className}>
      <defs>
        <linearGradient id="medfold-logo" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#34a399" />
          <stop offset="1" stopColor="#195b56" />
        </linearGradient>
      </defs>
      <rect width="32" height="32" rx="8" fill="url(#medfold-logo)" />
      <path d="M9 7.5h9.5L23 12v12.5H9z" fill="#fff" opacity="0.95" />
      <path d="M18.5 7.5V12H23" fill="#d5ecea" />
      <path d="M11 18.5h2.6l1.5-3.2 2.2 5.6 1.5-2.4H21" fill="none" stroke="#1f726b" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}
