import { useMemo } from 'react';
import { ArrowUp, CornerUpLeft, CornerUpRight, Flag, LocateFixed, Navigation, RotateCcw, ShieldAlert, X } from 'lucide-react';
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

export default function RouteGuidance({ route, pose, units = 'km', onExit }: GuidanceProps) {
  const state = useMemo(() => navigationState(route, pose), [route, pose]);
  const instruction = !route.live ? (route.steps?.[0]?.text || 'Follow the planned route') : state.progress?.instruction || 'Continue to your destination';
  const nextInstruction = (state.progress as { nextInstruction?: string } | null)?.nextInstruction;
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
    <span className={`stitch-guidance-mode${pose.estimated ? ' estimated' : ''}`}><i />{!route.live ? 'Offline route preview' : !state.usable ? 'Position uncertain' : pose.source === 'simulation' ? 'Simulation' : pose.estimated ? 'Sensor estimate · check road signs' : 'GPS positioning'}</span>
  </section>;
}

export function NavigationSummary({ route, pose, units = 'km', onExit }: GuidanceProps) {
  const state = useMemo(() => navigationState(route, pose), [route, pose]);
  const remaining = route.live ? state.progress?.remaining ?? route.distance : route.distance;
  const seconds = route.live ? state.progress?.seconds ?? route.duration : route.duration;
  const minutes = Math.max(1, Math.ceil(seconds / 60));
  const eta = new Date(Date.now() + seconds * 1000).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
  const speedAvailable = pose.ready && pose.speedAvailable !== false && Number.isFinite(pose.speed) && (pose.estimated ? pose.imuFresh !== false : pose.positionFresh !== false);
  const speed = Math.round(pose.speed * (units === 'mi' ? 2.236936 : 3.6));
  return <section className="navigation-summary" aria-label="Navigation trip summary">
    <div className="navigation-confidence"><ShieldAlert size={13}/><span>Position confidence {pose.ready&&Number.isFinite(pose.confidence)?Math.round(Math.max(0,Math.min(1,pose.confidence))*100)+'%':'unavailable'}</span><small>{pose.ready&&Number.isFinite(pose.accuracy)?'\u00b1'+Math.round(pose.accuracy)+' m estimate':'Waiting for sensors'}</small></div>
    <div className="navigation-speed"><strong>{speedAvailable ? speed : '—'}</strong><span>{units === 'mi' ? 'mph' : 'km/h'}</span></div>
    <div className="navigation-summary-main">
      {state.usable ? <><strong>{state.arrival ? 'Arrived' : `${minutes} min`}</strong><span>{formatRouteDistance(remaining, units)}{!state.arrival && ` · ${eta} ETA`}</span><small>{!route.live ? 'Planned route · offline estimate' : pose.estimated ? 'Sensor estimate · no live traffic' : 'Offline estimate · no live traffic'}</small></> : <><strong>Guidance paused</strong><span>Waiting for a reliable position</span><small>Route saved on the map</small></>}
    </div>
    {onExit ? <button className="navigation-exit" type="button" onClick={onExit}><X size={17} /><span>Exit</span></button> : <Navigation className="navigation-summary-symbol" size={22} />}
  </section>;
}
