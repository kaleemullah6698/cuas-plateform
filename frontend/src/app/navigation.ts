import {
  type LucideIcon,
  Bell,
  BellRing,
  BrainCircuit,
  FileText,
  History,
  LayoutDashboard,
  Map,
  Radar,
  Radio,
  ScrollText,
  ShieldAlert,
  SlidersHorizontal,
  Users,
} from 'lucide-react';

/**
 * The console's primary navigation (ARCHITECTURE.md §2.4, §3.6). Each section names the §7.4
 * permission that gates it; the nav rail hides a section a user cannot reach, and the route enforces
 * it (role-based UI is convenience — the server is the control). `permission: null` means any
 * authenticated operator.
 */
export interface NavSection {
  to: string;
  label: string;
  icon: LucideIcon;
  permission: string | null;
}

export const NAV_SECTIONS: NavSection[] = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard, permission: null },
  { to: '/map', label: 'Live Map', icon: Map, permission: 'map:read' },
  { to: '/tracks', label: 'Tracks', icon: Radar, permission: 'track:read' },
  { to: '/sensors', label: 'Sensor Health', icon: Radio, permission: 'sensor:read' },
  { to: '/timeline', label: 'Threat Timeline', icon: History, permission: 'alert:read' },
  { to: '/alerts', label: 'Alerts', icon: Bell, permission: 'alert:read' },
  { to: '/incidents', label: 'Incidents', icon: ShieldAlert, permission: 'incident:read' },
  { to: '/reports', label: 'Reports', icon: FileText, permission: 'report:read' },
  { to: '/config', label: 'Configuration', icon: SlidersHorizontal, permission: 'policy:read' },
  { to: '/ai-services', label: 'AI Services', icon: BrainCircuit, permission: 'ai:configure' },
  { to: '/users', label: 'User Management', icon: Users, permission: 'user:read' },
  { to: '/audit', label: 'Audit', icon: ScrollText, permission: 'audit:read' },
  { to: '/notifications', label: 'Notifications', icon: BellRing, permission: null },
];
