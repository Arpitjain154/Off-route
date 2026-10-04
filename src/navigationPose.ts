import type { Pose } from './types';

/** Validate the native boundary before any coordinate reaches MapLibre. */
export function safeNavigationPose(p: Pose): Pose | null {
  if (!p || !Number.isFinite(p.lat) || !Number.isFinite(p.lon) ||
      Math.abs(p.lat) > 85.051129 || Math.abs(p.lon) > 180) return null;
  const number = (value: number, fallback = 0) => Number.isFinite(value) ? value : fallback;
  const headingValid = Number.isFinite(p.heading);
  return {
    ...p,
    heading: headingValid ? ((p.heading % 360) + 360) % 360 : 0,
    headingReady: headingValid && p.headingReady !== false,
    speed: Math.max(0, number(p.speed)), accuracy: Math.max(0, number(p.accuracy)),
    confidence: Math.max(0, Math.min(1, number(p.confidence))),
    distance: Math.max(0, number(p.distance)), elapsed: Math.max(0, number(p.elapsed)),
    health: ['HEALTHY', 'DEGRADING', 'BLACKOUT'].includes(p.health) ? p.health : 'BLACKOUT',
  };
}
