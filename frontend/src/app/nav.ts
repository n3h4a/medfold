import type { ComponentType, SVGProps } from 'react'
import { ClipboardIcon, FlaskIcon, FolderIcon, HomeIcon, PillIcon } from '../components/icons.tsx'

export interface NavItem {
  to: string
  label: string
  icon: ComponentType<SVGProps<SVGSVGElement>>
  end?: boolean
}

/** The main pages, in navigation order. Also offered by the search palette. */
export const navItems: NavItem[] = [
  { to: '/', label: 'Overview', icon: HomeIcon, end: true },
  { to: '/documents', label: 'Documents', icon: FolderIcon },
  { to: '/labs', label: 'Test results', icon: FlaskIcon },
  { to: '/medications', label: 'Medicines', icon: PillIcon },
  { to: '/summary', label: 'Visit summary', icon: ClipboardIcon },
]
