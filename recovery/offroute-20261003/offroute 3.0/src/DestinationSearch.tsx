import { useEffect, useMemo, useRef, useState } from 'react';
import { ArrowLeft, ArrowUpDown, Search, LocateFixed, MapPin, LoaderCircle, ArrowRight, X, Car, Bike, Footprints, Route as RouteIcon, Navigation, Radio } from 'lucide-react';
import type { Pose } from './types';
import { calculateRoute, distanceKm, endpointName, loadDestinationPlaces, resolveEndpoint, searchPlaces, type Endpoint, type Place, type RoadRoute, type TravelMode } from './directions';
import './destination-search.css';

type Field = { text: string; selected: Endpoint | null };
type Props = { pose: Pose; route: RoadRoute | null; onRoute: (route: RoadRoute) => void; units?: 'km' | 'mi'; onOpenChange?: (open: boolean) => void; onNavigate?: (page: 'navigate'|'drives'|'sensors')=>void };
const currentField = (): Field => ({ text: 'Your current location', selected: { kind: 'current' } });

export default function DestinationSearch({ pose, route, onRoute, units = 'km', onOpenChange, onNavigate }: Props) {
  const [open, setOpen] = useState(false);
  const [start, setStart] = useState<Field>(currentField);
  const [destination, setDestination] = useState<Field>({ text: '', selected: null });
  const [active, setActive] = useState<'start' | 'destination'>('destination');
  const [places, setPlaces] = useState<Place[]>([]);
  const [recent,setRecent]=useState<string[]>(()=>{try{return JSON.parse(localStorage.getItem('idr-recent-places')||'[]');}catch{return [];}});
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [mode, setMode] = useState<TravelMode>('car');
  const [loadError, setLoadError] = useState('');
  const [highlight, setHighlight] = useState(-1);
  const dialog = useRef<HTMLDialogElement>(null), opener = useRef<HTMLButtonElement>(null);
  const startInput = useRef<HTMLInputElement>(null), destinationInput = useRef<HTMLInputElement>(null);
  const attempt = useRef(0), latestPose = useRef(pose); latestPose.current = pose;
  const selectedField = active === 'start' ? start : destination;
  const near = resolveEndpoint({ kind: 'current' }, pose);
  const query = selectedField.selected ? '' : selectedField.text;
  const nearLat = near ? Math.round(near.lat * 1000) / 1000 : undefined;
  const nearLon = near ? Math.round(near.lon * 1000) / 1000 : undefined;
  const results = useMemo(() => query || nearLat !== undefined ? searchPlaces(places, query, nearLat !== undefined && nearLon !== undefined ? { lat: nearLat, lon: nearLon } : undefined) : [...recent.map(id=>places.find(p=>p.id===id)).filter((p):p is Place=>!!p), ...places.filter(p=>p.type==='city'&&!recent.includes(p.id)).sort((a,b)=>a.name.localeCompare(b.name))].slice(0,8), [places, query, nearLat, nearLon, recent]);
  const formatDistance = (km: number) => `${(units === 'mi' ? km / 1.609344 : km).toFixed(1)} ${units}`;

  useEffect(() => { onOpenChange?.(open); }, [open, onOpenChange]);
  useEffect(() => () => onOpenChange?.(false), [onOpenChange]);
  useEffect(() => {
    if (!open) return;
    let alive = true;
    setLoading(true); setLoadError('');
    loadDestinationPlaces().then(p => { if (alive) setPlaces(p); }).catch(e => { if (alive) setLoadError(e.message); })
      .finally(() => { if (alive) setLoading(false); });
    dialog.current?.showModal();
    const previous = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    // Keep the full planner visible until the user intentionally focuses a field.
    return () => { alive = false; attempt.current++; dialog.current?.close(); document.body.style.overflow = previous; };
  }, [open]);
  useEffect(() => setHighlight(-1), [query, active]);
  useEffect(() => { if (highlight >= 0) document.getElementById(`place-result-${highlight}`)?.scrollIntoView({ block: 'nearest' }); }, [highlight]);

  const close = () => { attempt.current++; setOpen(false); setBusy(false); opener.current?.focus(); };
  const show = () => {
    const field = (p: Place): Field => p.id === 'current' ? currentField() : { text: p.name, selected: { kind: 'place', place: p } };
    setStart(route ? field(route.start) : currentField());
    setDestination(route ? field(route.destination) : { text: '', selected: null });
    setActive('destination'); setError(''); setBusy(false); setOpen(true); setMode(route?.mode ?? 'car');
  };
  const choose = (selected: Endpoint) => {
    const value = { text: endpointName(selected), selected };
    if(selected.kind==='place'){const ids=[selected.place.id,...recent.filter(id=>id!==selected.place.id)].slice(0,8);setRecent(ids);localStorage.setItem('idr-recent-places',JSON.stringify(ids));}
    if (active === 'start') { setStart(value); setActive('destination'); destinationInput.current?.focus(); }
    else { setDestination(value); destinationInput.current?.blur(); }
    setError(''); setHighlight(-1);
  };
  const from = resolveEndpoint(start.selected, pose), to = resolveEndpoint(destination.selected, pose);
  const waiting = (start.selected?.kind === 'current' || destination.selected?.kind === 'current') && !near;
  const confirm = async () => {
    const a = resolveEndpoint(start.selected, latestPose.current), b = resolveEndpoint(destination.selected, latestPose.current);
    if (!a || !b || busy) return;
    const token = ++attempt.current; setBusy(true); setError('');
    try { const result = await calculateRoute(a, b, mode); if (token === attempt.current) { onRoute(result); close(); } }
    catch (e) { if (token === attempt.current) setError(e instanceof Error ? e.message : 'Unable to find directions. Please try again.'); }
    finally { if (token === attempt.current) setBusy(false); }
  };
  const keydown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'ArrowDown') { event.preventDefault(); setHighlight(v => Math.min(v + 1, results.length - 1)); }
    if (event.key === 'ArrowUp') { event.preventDefault(); setHighlight(v => Math.max(0, v - 1)); }
    if (event.key === 'Enter') { event.preventDefault(); if (highlight >= 0 && results[highlight]) choose({ kind: 'place', place: results[highlight] }); else if (from && to) void confirm(); }
  };

  return <div className="destination-header">
    <button ref={opener} className="destination-launcher" onClick={show} aria-label={route ? 'Edit destination' : 'Search offline destination'} aria-haspopup="dialog">
      <Search size={20} /><span>{route ? route.destination.name : 'Search offline destination or trail…'}</span>{route && <ArrowRight size={18} />}
    </button>
    {open && <dialog ref={dialog} className={`destination-dialog${pose.source === 'device' ? ' native' : ''}`} aria-labelledby="destination-title" onCancel={event => { event.preventDefault(); close(); }}>
      <div className="destination-dialog-heading">
        <button type="button" className="destination-back" aria-label="Back to map" onClick={close}><ArrowLeft size={21} /></button>
        <h2 id="destination-title">Plan Offline Route</h2>
        <button type="button" className="destination-swap" aria-label="Swap starting point and destination" disabled={busy} onClick={() => { setStart(destination); setDestination(start); setError(''); setHighlight(-1); }}><ArrowUpDown size={20} /></button>
      </div>
      <div className="destination-planner-scroll">
        <div className="destination-fields">
          {(['start', 'destination'] as const).map(name => {
            const field = name === 'start' ? start : destination, update = name === 'start' ? setStart : setDestination;
            return <div className={`destination-field${active === name ? ' focused' : ''}`} key={name}>
              {name === 'start' ? <LocateFixed size={20} /> : <MapPin size={20} />}
              <label><span>{name === 'start' ? 'Starting point' : 'Destination'}</span>
                <input ref={name === 'start' ? startInput : destinationInput} value={field.text} placeholder={name === 'start' ? 'Choose a starting point' : 'Search an offline destination'} disabled={busy}
                  role="combobox" aria-autocomplete="list" aria-expanded={active === name} aria-controls="destination-results" aria-activedescendant={active === name && highlight >= 0 ? `place-result-${highlight}` : undefined}
                  autoComplete="off" enterKeyHint="search" onKeyDown={keydown} onFocus={e => { setActive(name); setHighlight(-1); e.target.select(); }}
                  onChange={e => { update({ text: e.target.value, selected: null }); setError(''); }} />
              </label>
              <button type="button" className="destination-clear" disabled={busy} aria-label={`Clear ${name === 'start' ? 'starting point' : 'destination'}`} onClick={() => { update({ text: '', selected: null }); setActive(name); (name === 'start' ? startInput : destinationInput).current?.focus(); }}><X size={15} /></button>
            </div>;
          })}
        </div>
        <div className="destination-results-area">
          {active === 'start' && <button type="button" className="destination-current" disabled={busy} onClick={() => choose({ kind: 'current' })}><LocateFixed size={19} /><span>Your current location<small>{near ? pose.estimated ? 'Use the sensor-estimated position' : 'Use the live GPS position' : 'Waiting for a device fix · or choose a place'}</small></span></button>}
          <div className="destination-results-heading">{query ? 'SEARCH RESULTS' : near ? 'NEARBY OFFLINE PLACES' : recent.length?'RECENT & OFFLINE PLACES':'OFFLINE CITIES & PLACES'}</div>
          {loading ? <p className="destination-message" role="status">Loading offline places…</p> : loadError ? <p className="destination-message" role="alert">{loadError}</p> : results.length === 0 ? <p className="destination-message" role="status">No places found. Try a nearby area or another spelling.</p> : null}
          <div id="destination-results" role="listbox" aria-label={`Places for ${active === 'start' ? 'starting point' : 'destination'}`}>
            {results.map((p, i) => <button type="button" role="option" aria-selected={highlight === i || selectedField.selected?.kind === 'place' && selectedField.selected.place.id === p.id} id={`place-result-${i}`} className={`destination-result${highlight === i ? ' highlighted' : ''}`} key={p.id} disabled={busy} onMouseDown={e => e.preventDefault()} onClick={() => choose({ kind: 'place', place: p })}>
              <MapPin size={19} /><span><strong>{p.name}</strong><small>{p.type}{p.address && ` · ${p.address}`}</small></span>{near && <em>{formatDistance(distanceKm(near, p))}<small>away</small></em>}
            </button>)}
          </div>
        </div>
        <div className="destination-plan-actions">
          <div className="destination-modes" role="group" aria-label="Travel mode">
            <button type="button" disabled={busy} aria-pressed={mode === 'car'} onClick={() => setMode('car')}><Car size={19} /><span>Drive</span></button>
            <button type="button" disabled={busy} aria-pressed={mode === 'motorcycle'} onClick={() => setMode('motorcycle')}><Bike size={19} /><span>Motorcycle</span></button>
            <button type="button" disabled aria-describedby="unavailable-modes"><Footprints size={19} /><span>Walk</span></button>
            <button type="button" disabled aria-describedby="unavailable-modes"><Bike size={19} /><span>Cycle</span></button>
          </div>
          <p id="unavailable-modes" className="destination-mode-note">Walking and cycling routes are not available in this offline map.</p>
          <div className="destination-preview">
            <RouteIcon size={21} /><div><strong>{destination.selected ? endpointName(destination.selected) : 'Choose your destination'}</strong><small>{destination.selected ? 'Offline road route · calculated on your device' : 'Search places stored in your offline map'}</small><p><span>{start.selected ? endpointName(start.selected) : 'Choose a starting point'}</span><ArrowRight size={14} /><span>{destination.selected ? endpointName(destination.selected) : 'Destination'}</span></p></div>
          </div>
          <div className="destination-footer">
            {waiting && <p role="status">Waiting for your current position. You can select a starting point manually.</p>}
            {error && <p className="destination-error" role="alert">{error}</p>}
            <button type="button" className="destination-confirm" disabled={!from || !to || busy} onClick={() => void confirm()}>{busy ? <LoaderCircle className="spin" size={19} /> : <RouteIcon size={19} />}{busy ? 'Calculating offline route…' : 'Get directions'}</button>
            <small>Distance and travel time appear after route calculation.</small>
          </div>
        </div>
      </div>
      {onNavigate&&<nav className="planner-navigation" aria-label="Planner navigation">{([{id:'navigate',label:'Navigate',icon:Navigation},{id:'drives',label:'Journeys',icon:RouteIcon},{id:'sensors',label:'Sensors',icon:Radio}] as const).map(item=><button type="button" key={item.id} onClick={()=>{close();onNavigate(item.id);}}><item.icon size={18}/>{item.label}</button>)}</nav>}
    </dialog>}
  </div>;
}
