import { useMemo, useState } from 'react';
import { ArrowUp, CornerUpLeft, CornerUpRight, Flag, LocateFixed, RotateCcw, ShieldAlert, X } from 'lucide-react';
import type { Pose } from './types';
import type { RoadRoute } from './directions';
import { routeGuidance } from './routeProgress';
import './route-guidance.css';

type GuidanceProps = { route: RoadRoute; pose: Pose; units?: 'km' | 'mi'; onExit?: () => void };

export function formatRouteDistance(metres: number, units: 'km' | 'mi' = 'km') {
  if (units === 'mi') return metres < 160.9344 ? `${Math.max(0, Math.round(metres * 3.28084 / 10) * 10)} ft` : `${(metres / 1609.344).toFixed(1)} mi`;
  return metres < 1000 ? `${Math.max(0, Math.round(metres / 10) * 10)} m` : `${(metres / 1000).toFixed(1)} km`;
}

function navigationState(route: RoadRoute, pose: Pose) {
  const precision = pose.estimated ? pose.accuracy : Math.max(pose.gpsAccuracy ?? pose.accuracy,pose.accuracy);
  const valid = (pose.gpsAccuracy===undefined||(Number.isFinite(pose.gpsAccuracy)&&pose.gpsAccuracy>=0)) && Number.isFinite(pose.lat) && Number.isFinite(pose.lon) && Number.isFinite(pose.accuracy) && Number.isFinite(precision) && precision >= 0 && pose.accuracy >= 0;
  const fresh = pose.estimated
    ? pose.imuFresh !== false && pose.trackingAvailable !== false
    : pose.positionFresh !== false && pose.health !== 'BLACKOUT' && (pose.gpsAge === undefined || (pose.gpsAge >= 0 && pose.gpsAge <= 8));
  const usable = !route.live || (pose.ready && valid && fresh && precision <= 50);
  const progress = valid && route.coordinates.length >= 2 ? routeGuidance(route, pose) : null;
  const arrival = Boolean(route.live && usable && !pose.estimated && precision <= 25 && progress && progress.remaining < 25 && progress.offRoute < 25);
  const reason = !pose.ready ? 'Waiting for a position fix'
    : !fresh ? 'Waiting for a fresh position'
    : 'Position uncertainty is too high';
  return { usable, arrival, progress, reason };
}

function ManeuverIcon({ instruction, arrived, paused }: { instruction: string; arrived: boolean; paused: boolean }) {
  if (paused) return <ShieldAlert size={25} />;
  if (arrived) return <Flag size={25} />;
  if (/u-turn|u turn/i.test(instruction)) return <RotateCcw size={25} />;
  if (/left/i.test(instruction)) return <CornerUpLeft size={25} />;
  if (/right/i.test(instruction)) return <CornerUpRight size={25} />;
  return <ArrowUp size={25} />;
}

export function navigationTelemetry(pose: Pose) {
  const imu = pose.imuFresh === true;
  const gps = pose.ready && pose.positionFresh !== false && pose.health !== 'BLACKOUT' && (pose.gpsAge === undefined || pose.gpsAge >= 0 && pose.gpsAge <= 8);
  const inertial = pose.ready && pose.estimated === true && imu && pose.trackingAvailable !== false;
  const available = pose.estimated ? inertial : gps;
  const percent = (v: number) => Number.isFinite(v) ? Math.round(Math.max(0, Math.min(1, v)) * 100) : null;
  const mode = pose.source === 'simulation' ? 'SIMULATION' : !pose.ready ? 'ACQUIRING POSITION' : inertial ? 'GPS LOST \u00b7 IMU TRACKING' : pose.estimated ? 'POSITION UNAVAILABLE' : !gps ? 'GPS LOST \u00b7 POSITION HELD' : pose.health === 'DEGRADING' ? (imu ? 'GPS DEGRADED \u00b7 IMU READY' : 'GPS DEGRADED') : 'GPS TRACKING';
  return { mode, available, inertial, gpsTrust: pose.ready ? percent(pose.gpsTrust) : null, confidence: available ? percent(pose.confidence) : null, imuLabel: imu ? inertial ? 'IMU tracking' : 'IMU ready' : 'IMU unavailable', gpsLabel: gps && !pose.estimated ? 'GPS active' : 'GPS lost' };
}

export default function RouteGuidance({ route, pose, units = 'km', onExit }: GuidanceProps) {
  const state = useMemo(() => navigationState(route, pose), [route, pose]);
  const instruction = !route.live ? (route.steps?.[0]?.text || 'Follow the planned route') : state.progress?.instruction || 'Continue to your destination';
  const nextInstruction = state.progress?.nextInstruction;
  const telemetry = navigationTelemetry(pose);
  return <section className={`stitch-guidance${!state.usable ? ' is-paused' : ''}`} aria-label="Turn by turn guidance">
    <div className="stitch-maneuver">
      <span className="stitch-maneuver-icon"><ManeuverIcon instruction={instruction} arrived={state.arrival} paused={!state.usable} /></span>
      <div className="stitch-maneuver-copy">
        <span className="stitch-maneuver-distance">{!state.usable ? 'Guidance paused' : state.arrival ? 'You have arrived' : !route.live ? 'Route preview' : `In ${formatRouteDistance(state.progress?.toTurn || 0, units)}`}</span>
        <strong>{!state.usable ? state.reason : state.arrival ? route.destination.name : instruction}</strong>
      </div>
      {onExit && <button className="stitch-guidance-close" type="button" onClick={onExit} aria-label="Exit navigation"><X size={20} /></button>}
    </div>
    <div className="stitch-next-step">
      {!state.usable ? <><LocateFixed size={14} /><span>Route retained · wait for a reliable position</span></> : <><ArrowUp size={14} /><span>{nextInstruction && !state.arrival ? `Then ${nextInstruction}` : route.destination.name}</span></>}
    </div>
    <span role="status" aria-live="polite" className={`stitch-guidance-mode${pose.estimated || !telemetry.available ? ' estimated' : ''}`}><i />{!route.live ? 'Offline route preview' : telemetry.mode}</span>
  </section>;
}

export function NavigationSummary({ route, pose, units = 'km' }: GuidanceProps) {
  const [detail, setDetail] = useState<'confidence' | 'gps' | 'imu'>('confidence');
  const state = useMemo(() => navigationState(route, pose), [route, pose]);
  const remaining = route.live ? state.progress?.remaining ?? route.distance : route.distance;
  const seconds = route.live ? state.progress?.seconds ?? route.duration : route.duration;
  const minutes = Math.max(1, Math.ceil(seconds / 60));
  const eta = new Date(Date.now() + seconds * 1000).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
  const speedAvailable = pose.ready && pose.speedAvailable !== false && Number.isFinite(pose.speed) && (pose.estimated ? pose.imuFresh !== false && pose.trackingAvailable !== false : pose.positionFresh !== false && pose.health !== 'BLACKOUT');
  const speed = Math.round(pose.speed * (units === 'mi' ? 2.236936 : 3.6));
  const telemetry = navigationTelemetry(pose);
  const accuracy = telemetry.available && Number.isFinite(pose.accuracy) && pose.accuracy >= 0 ? '\u00b1' + Math.round(pose.accuracy) + ' m estimate' : 'Position unavailable';
  return <section className="navigation-summary navigation-instruments" aria-label="Navigation trip summary">
    <div className="navigation-source-chips" aria-label="Live positioning sources"><button type="button" aria-label="Show GPS details" aria-pressed={detail === 'gps'} className={telemetry.inertial ? 'source-lost' : ''} onClick={()=>setDetail(detail === 'gps' ? 'confidence' : 'gps')}>{telemetry.gpsLabel}</button><button type="button" aria-label="Show IMU details" aria-pressed={detail === 'imu'} onClick={()=>setDetail(detail === 'imu' ? 'confidence' : 'imu')}>{telemetry.imuLabel}</button></div>
    <div className="navigation-speed" aria-label="Current speed"><strong>{speedAvailable ? speed : '\u2014'}</strong><span>{units === 'mi' ? 'mph' : 'km/h'}</span></div>
    <div className="navigation-confidence-card">
      <strong><i className={telemetry.inertial ? 'estimated' : ''}/>{detail === 'gps' ? 'GPS signal' : detail === 'imu' ? 'Inertial tracking' : 'Position confidence ' + (telemetry.confidence === null ? '\u2014' : telemetry.confidence + '%')}</strong>
      <span>{detail === 'gps' ? `${pose.ready ? pose.satellites : '\u2014'} satellites \u00b7 ${pose.gpsAge !== undefined && pose.gpsAge >= 0 ? 'Fix ' + Math.round(pose.gpsAge) + ' s ago' : 'No recent fix'}` : detail === 'imu' ? `${telemetry.imuLabel} \u00b7 ${accuracy}` : `${telemetry.inertial ? 'Sensor estimate' : telemetry.available ? 'GPS positioning' : 'Position held'} \u00b7 ${accuracy}`}</span>
      {state.usable ? <small>{state.arrival ? 'Arrived' : `${minutes} min \u00b7 ${formatRouteDistance(remaining, units)} \u00b7 ${eta} ETA`}</small> : <small>Waiting for a reliable position</small>}
    </div>
    <div className="navigation-gps-ring" aria-label={'GPS advisor trust ' + (telemetry.gpsTrust === null ? 'unavailable' : telemetry.gpsTrust + '%')} style={{background: `conic-gradient(var(--green) ${(telemetry.gpsTrust ?? 0) * 3.6}deg, var(--line) 0deg)`}}><div><span>GPS trust</span><strong>{telemetry.gpsTrust === null ? '\u2014' : telemetry.gpsTrust + '%'}</strong></div></div>
  </section>;
}
