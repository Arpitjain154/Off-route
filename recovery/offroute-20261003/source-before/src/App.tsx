import {useEffect,useRef,useState} from 'react';
import {Navigation,Route,Radio,Settings2,LocateFixed,Layers,Compass,ShieldCheck,ChevronDown,ChevronRight,MapPin,Activity,Satellite,Clock,HardDrive,ArrowLeft,X,CloudUpload,Download,RefreshCw,Play,Square,Info,Wifi,Smartphone,SlidersHorizontal,Check,AlertCircle} from 'lucide-react';
import {ResponsiveContainer,AreaChart,Area,XAxis,YAxis,Tooltip,CartesianGrid} from 'recharts';
import MapView from './MapView';
import DestinationSearch from './DestinationSearch';
import RouteGuidance,{NavigationSummary} from './RouteGuidance';
import {useRouteNavigation} from './useRouteNavigation';
import {api,client,errorMessage,readSettings} from './api';
import {native,DrEngine,SensorPlugin,ModelPlugin,initialPose,ensureNativeEngine,startEngine,stopEngine,getPose,beginLog,endLog,uploadDrive,updateModels} from './engine';
import {RecordingLifecycle} from './recordingLifecycle';
import modelManifest from './model-manifest.json';
import type {Drive,Pose,Report,Settings} from './types';
import type {RoadRoute} from './directions';
import './stitch-shell.css';
import CloudSettings from './CloudSettings';
import {Cloud,useCloud} from './cloud';
import {cloudJourneyLabel,canUploadJourney} from './cloudState';

const readDrives=():Drive[]=>{try{const d=JSON.parse(localStorage.getItem('idr-drives')||'[]');return Array.isArray(d)?d:[];}catch{return [];}};
const readRoute=():RoadRoute|null=>{try{const r=JSON.parse(localStorage.getItem('idr-active-route')||'null');return r?.live&&r.destination?.id&&r.start?.id&&Array.isArray(r.coordinates)&&r.coordinates.length>1&&r.coordinates.every((p:number[])=>p.length===2&&p.every(Number.isFinite))?r:null;}catch{return null;}};
const elapsed=(n:number)=>{if(!Number.isFinite(n))return 'Unavailable';return Math.floor(n/3600)?Math.floor(n/3600)+'h '+Math.floor(n%3600/60)+'m':Math.floor(n/60)+'m '+Math.floor(n%60)+'s';};
const num=(n:unknown,d=0)=>typeof n==='number'&&Number.isFinite(n)?n.toFixed(d):'—';
function positionLabel(p:Pose,running:boolean){
 if(!running)return native?'Connecting to sensors':'Browser preview';
 if(!p.ready)return 'Acquiring GPS';
 if(p.estimated)return 'GPS limited · inertial estimate';
 if(p.positionFresh===false||p.health==='BLACKOUT')return 'GPS unavailable · last position';
 if(p.health==='DEGRADING')return 'GPS signal degraded';
 return 'GPS available';
}
type Identity={sessionId:string;startedAt:string;route?:string;mount?:Settings['mount'];source?:Drive['source']};
export default function App(){
 const cloud=useCloud();
 const [page,setPage]=useState<'navigate'|'drives'|'sensors'>('navigate');
 const [settings,setSettings]=useState(readSettings),settingsRef=useRef(settings);settingsRef.current=settings;
 const [pose,setPose]=useState(initialPose),poseRef=useRef(pose);poseRef.current=pose;
 const [running,setRunning]=useState(false),[logging,setLogging]=useState(false),[busy,setBusy]=useState(false);
 const [recoveryWarning,setRecoveryWarning]=useState(()=>localStorage.getItem('idr-recovery-logs')?'Interrupted recording metadata is retained for review.':'' ),[error,setError]=useState(''),[toast,setToast]=useState(''),[online,setOnline]=useState(false);
 const [headingUp,setHeadingUp]=useState(true);
 const [follow,setFollow]=useState(true),[recenter,setRecenter]=useState(0),[showTrack,setShowTrack]=useState(true);
 const [expanded,setExpanded]=useState(false),[plannerOpen,setPlannerOpen]=useState(false);
 const [route,setRoute]=useState<RoadRoute|null>(readRoute),routeRef=useRef(route);routeRef.current=route;
 const rerouteStatus=useRouteNavigation(pose,route,setRoute);
 const [drives,setDrives]=useState<Drive[]>(readDrives),[selected,setSelected]=useState<Drive|null>(null);
 const [report,setReport]=useState<Report|null>(null),[reportState,setReportState]=useState(''),[refreshing,setRefreshing]=useState(false);
 const [settingsOpen,setSettingsOpen]=useState(false),[draft,setDraft]=useState(settings);
 const [onboarding,setOnboarding]=useState(!native&&!localStorage.getItem('idr-onboarded'));
 const [modelVersions,setModelVersions]=useState(modelManifest.models.map(m=>m.name+' · '+m.version+' · IO-VNBD'));
 const [systemDark,setSystemDark]=useState(window.matchMedia('(prefers-color-scheme: dark)').matches);
 const theme=settings.theme==='system'?(systemDark?'dark':'light'):settings.theme;
 const dialog=useRef<HTMLDialogElement>(null),identity=useRef<Identity|null>(null);
 const recorder=useRef<RecordingLifecycle|null>(null);
 const detailEpoch=useRef(0);
 const saveDrives=(items:Drive[])=>{localStorage.setItem('idr-drives',JSON.stringify(items));setDrives(items);};
 if(!recorder.current)recorder.current=new RecordingLifecycle(async()=>{
   const s=settingsRef.current,p=poseRef.current;
   const id:Identity={sessionId:crypto.randomUUID(),startedAt:new Date().toISOString(),route:routeRef.current?.destination.name||'Local journey',mount:s.mount,source:p.source};
   // Persist recovery identity before opening the native file.
   localStorage.setItem('idr-active-log',JSON.stringify(id));
   try{await beginLog(s,id.sessionId);identity.current=id;setLogging(true);}
   catch(e){localStorage.removeItem('idr-active-log');throw e;}
 },async()=>{
   const id=identity.current;
   if(!id)throw Error('Recording identity unavailable. Export diagnostics before restarting the app.');
   const info=await endLog().catch(()=>SensorPlugin.recoverLog({sessionId:id.sessionId}));
   const drive:Drive={_id:id.sessionId,sessionId:id.sessionId,startedAt:id.startedAt,uploadedAt:new Date().toISOString(),filename:id.sessionId+'.drlog',phoneModel:info.phoneModel,mount:id.mount||settingsRef.current.mount,route:'mixed',title:id.route||'Local journey',notes:'Collected locally during navigation for training review.',durationSeconds:info.durationSeconds,distanceMeters:info.distanceMeters,sizeBytes:info.sizeBytes,source:id.source||poseRef.current.source,localPath:info.path,status:'pending'};
   saveDrives([drive,...readDrives().filter(d=>d.sessionId!==id.sessionId)]);
   localStorage.removeItem('idr-active-log');identity.current=null;setLogging(false);
 });
 const changeRecording=async(desired:boolean)=>{
   setBusy(true);
   try{await recorder.current!.set(desired);return true;}
   catch(e){setToast(errorMessage(e));return false;}
   finally{setBusy(false);}
 };
 useEffect(()=>{
   document.documentElement.dataset.theme=theme;
   if(native)void DrEngine.setAppearance({dark:theme==='dark'}).catch(()=>{});
   localStorage.setItem('idr-settings',JSON.stringify(settings));
 },[settings,theme]);
 useEffect(()=>{const query=window.matchMedia('(prefers-color-scheme: dark)');const listener=()=>setSystemDark(query.matches);query.addEventListener('change',listener);return()=>query.removeEventListener('change',listener);},[]);
 useEffect(()=>{if(route?.live)localStorage.setItem('idr-active-route',JSON.stringify(route));else localStorage.removeItem('idr-active-route');},[route]);
 useEffect(()=>{
   if(!native)return;let alive=true;
   const resume=async()=>{
    if(document.visibilityState==='hidden')return;
    try{
      await ensureNativeEngine();await recorder.current!.settled();const p=await getPose();if(!alive)return;
      setPose(p);setError('');
      const savedLog=localStorage.getItem('idr-active-log');
      if(!p.logging&&savedLog){
        const preserve=async(e:unknown)=>{
          const entries=JSON.parse(localStorage.getItem('idr-recovery-logs')||'[]');
          localStorage.setItem('idr-recovery-logs',JSON.stringify([...entries,{identity:savedLog,error:errorMessage(e),at:new Date().toISOString()}]));
          localStorage.removeItem('idr-active-log');identity.current=null;setLogging(false);
          setSettings(s=>({...s,learningEnabled:false}));
          setRecoveryWarning('An interrupted recording needs review. Its raw file and identity are retained; collection is paused. Navigation is available.');
        };
        try{identity.current=JSON.parse(savedLog);recorder.current!.adopt(true);await recorder.current!.recover(preserve);}
        catch(e){setRecoveryWarning('Recording recovery unavailable: '+errorMessage(e));setSettings(s=>({...s,learningEnabled:false}));}
      }
      if(p.logging&&!recorder.current!.active){
        const saved=localStorage.getItem('idr-active-log');
        if(saved){try{identity.current=JSON.parse(saved);recorder.current!.adopt(true);setLogging(true);}catch{setError('An existing recording needs recovery. Export diagnostics from Sensors.');}}
        else setError('An existing recording needs recovery. Export diagnostics from Sensors.');
      }
      setRunning(true);
    }catch(e){if(alive){setError(errorMessage(e));setRunning(false);}}
   };
   void resume();document.addEventListener('visibilitychange',resume);
   return()=>{alive=false;document.removeEventListener('visibilitychange',resume);};
 },[]);
 useEffect(()=>{
   if(!running)return;let alive=true;let timer:ReturnType<typeof setTimeout>;
   const poll=async()=>{try{const p=await getPose();if(alive){setPose(p);setError('');}}
    catch(e){if(alive)setError(errorMessage(e));}
    if(alive)timer=setTimeout(poll,100);};
   void poll();return()=>{alive=false;clearTimeout(timer);};
 },[running]);
 // Page changes never own the native sensor or log lifecycle.
 useEffect(()=>{
   if(native&&route?.live&&running&&pose.ready&&settings.learningEnabled!==false)void changeRecording(true);
 },[route?.live,running,pose.ready,settings.learningEnabled]);
 useEffect(()=>{
   let active=true;const check=()=>api.health().then(()=>active&&setOnline(true)).catch(()=>active&&setOnline(false));
   void check();const timer=setInterval(check,20000);return()=>{active=false;clearInterval(timer);};
 },[settings.apiUrl,settings.apiKey]);
 useEffect(()=>{if(!toast)return;const timer=setTimeout(()=>setToast(''),6500);return()=>clearTimeout(timer);},[toast]);
 useEffect(()=>{if(page==='drives')void refreshDrives();},[page]);
 useEffect(()=>{if(native)void ModelPlugin.checkForUpdate().then(r=>setModelVersions(r.models.map(m=>m.name+' · '+m.version+(m.placeholder?' · placeholder':'')))).catch(()=>{});},[]);
 useEffect(()=>{if(settingsOpen)dialog.current?.showModal();else dialog.current?.close();},[settingsOpen]);
 const openSettings=()=>{setDraft(settings);setSettingsOpen(true);};
 async function refreshDrives(){
  setRefreshing(true);
  try{
   const remote=await api.drives(),local=readDrives();
   const merged=[...local.filter(d=>!remote.some(r=>r.sessionId===d.sessionId)),...remote.map(d=>({...local.find(l=>l.sessionId===d.sessionId),...d,status:'uploaded' as const}))];
   saveDrives(merged);setOnline(true);
  }catch{setDrives(readDrives());setOnline(false);}finally{setRefreshing(false);}
 }
 async function showDetail(d:Drive){
   const epoch=++detailEpoch.current;setSelected(d);setReport(null);setReportState('Loading accuracy report…');
   if(d.status==='pending'){setReportState('No measured drift available. Upload this journey to request an accuracy report.');return;}
   try{const result=await api.report(d._id);if(epoch===detailEpoch.current){setReport(result);setReportState('');}}catch{if(epoch===detailEpoch.current)setReportState('Accuracy report unavailable. Your journey remains saved.');}
 }
 async function upload(d:Drive){
   setBusy(true);try{
    const remote=await uploadDrive(d);const merged={...d,...remote,localPath:d.localPath,status:'uploaded' as const};
    saveDrives(readDrives().map(v=>v.sessionId===d.sessionId?merged:v));setSelected(merged);setToast('Journey uploaded.');setOnline(true);
   }catch(e){setToast(errorMessage(e)+' Your local recording is retained.');}finally{setBusy(false);}
 }
 async function exitNavigation(){
   if(!(await changeRecording(false)))return;
   setRoute(null);setExpanded(false);setToast('Navigation ended. Any recorded journey is saved locally.');
 }
 async function savePreferences(){
   if(draft.learningEnabled===false&&!(await changeRecording(false)))return;
   setSettings(draft);setSettingsOpen(false);setToast('Preferences saved.');
 }
 const distance=(m:number)=>Number.isFinite(m)?(m/(settings.units==='km'?1000:1609.344)).toFixed(1)+' '+settings.units:'Unavailable';
 const active=!!route?.live;
 const confidence=pose.ready&&Number.isFinite(pose.confidence)?Math.round(Math.max(0,Math.min(1,pose.confidence))*100):null;
 const fresh=pose.ready&&(pose.positionFresh!==false||pose.estimated);
 const warning=!!error||!fresh||pose.estimated||pose.health!=='HEALTHY';
 const totalDistance=drives.reduce((sum,d)=>sum+(Number.isFinite(d.distanceMeters)?d.distanceMeters:0),0);
 const totalBytes=drives.reduce((sum,d)=>sum+(Number.isFinite(d.sizeBytes)?d.sizeBytes:0),0);
 const newest=[...drives].sort((a,b)=>Date.parse(b.startedAt)-Date.parse(a.startedAt));
 const go=(next:typeof page)=>{detailEpoch.current++;setPage(next);setSelected(null);};
 const diagnostics=<>
   <div className="telemetry-list">
    {[
     ['Position source',positionLabel(pose,running)],
     ['Speed source',pose.speedSource||'Unavailable'],
     ['Satellite count',pose.ready?String(pose.satellites):'Unavailable'],
     ['GNSS fix age',pose.gpsAge!==undefined&&pose.gpsAge>=0?num(pose.gpsAge,1)+' s':'Unavailable'],
     ['Estimated position error',pose.ready?num(pose.accuracy,1)+' m':'Unavailable'],
     ['Map matching',pose.mapStatus||'Unavailable'],
     ['Mount alignment',pose.fusionStatus||'Unavailable'],
     ['IMU samples',pose.imuFresh===undefined?'Unavailable':pose.imuFresh?'Receiving':'Unavailable / stale'],
    ].map(([label,value])=><div key={label}><span>{label}</span><strong>{value}</strong></div>)}
   </div>
   <p className="muted">Confidence describes the engine’s estimate. Measured drift requires a reference journey.</p>
 </>;
 return <div className={'stitch-app '+(active?'navigating':'')}>
   <div className={'map-page '+(page!=='navigate'?'page-hidden':'')} aria-hidden={page!=='navigate'}>
    <div className="stitch-map">
     <MapView pose={pose} follow={follow} recenter={recenter} route={showTrack} destinationRoute={route} onMove={()=>setFollow(false)} theme={theme} headingUp={headingUp}/>
    </div>
    {!active&&<div className="stitch-search">
      <DestinationSearch pose={pose} route={route} units={settings.units} onOpenChange={setPlannerOpen} onNavigate={go} onRoute={r=>{setPlannerOpen(false);setRoute(r);setFollow(r.live);setHeadingUp(true);setRecenter(v=>v+1);}}/>
      <button className="profile-button" aria-label="Open settings" onClick={openSettings}><Settings2 size={19}/></button>
    </div>}
    {active?<RouteGuidance route={route!} pose={pose} units={settings.units} onExit={()=>void exitNavigation()}/>:null}
    {!active&&!plannerOpen&&<div className={'position-status '+(warning?'warn':'healthy')+(active?' in-navigation':'')} role="status"><span className="status-dot"/>{positionLabel(pose,running)}<span className="imu-pill"><Activity size={13}/>{pose.imuFresh?'IMU active':'IMU '+(running?'waiting':'offline')}</span></div>}
    <div className="stitch-map-controls">
     <button aria-label={active?(headingUp?"Switch to north up":"Follow travel direction"):"North up"} onClick={()=>{if(active){setHeadingUp(!headingUp);setFollow(true);}else setFollow(false);setRecenter(v=>v+1);}}><Compass size={22}/><small>{active&&headingUp?"UP":"N"}</small></button>
     <button aria-label="Recenter map" onClick={()=>{setFollow(true);setHeadingUp(true);setRecenter(v=>v+1);}}><LocateFixed size={23}/></button>
     {active?<button aria-label="Navigation sensor details" onClick={()=>go('sensors')}><Info size={21}/></button>:<button aria-label="Toggle driven path" aria-pressed={showTrack} onClick={()=>setShowTrack(!showTrack)}><Layers size={21}/></button>}
    </div>
    {error&&<div className="map-message" role="alert">{error}<button onClick={()=>void DrEngine.backgroundSettings()}>Location settings</button></div>}
    {!native&&<div className="preview-label">Browser preview · live sensors require Android</div>}
    {!active&&!plannerOpen&&<section className={'stitch-sheet '+(expanded?'expanded':'collapsed')}>
     <button className="sheet-handle" aria-label={expanded?'Collapse position details':'Expand position details'} aria-expanded={expanded} onClick={()=>setExpanded(!expanded)}><span/><ChevronDown size={16} style={{transform:expanded?'none':'rotate(180deg)'}}/></button>
     <div className="sheet-title"><Navigation size={20}/><div><h2>{route?.destination.name||'YOUR LOCATION'}</h2><p>{pose.ready?num(pose.lat,5)+', '+num(pose.lon,5):'Waiting for a location fix'}</p></div><button aria-label="Open sensor insights" onClick={()=>go('sensors')}><SlidersHorizontal size={19}/></button><span className={'confidence-chip '+(warning?'amber':'')}>{confidence===null?'—':confidence+'%'}</span></div>
     {expanded&&<div className="sheet-body">
      <div className="confidence-card-new"><div><ShieldCheck size={18}/><span>POSITION CONFIDENCE</span><strong>{confidence===null?'Unavailable':confidence+'%'}</strong></div><progress max={100} value={confidence??0}/><div className="confidence-meta"><span>±{pose.ready?num(pose.accuracy,1):'—'} m estimated error</span><span><Clock size={13}/>{pose.gpsAge!==undefined&&pose.gpsAge>=0?'Fix '+num(pose.gpsAge)+' s ago':'No GPS fix'}</span></div></div>
      <div className="sheet-metrics"><div><Radio size={21}/><span>IMU CAPTURE<strong>{pose.imuFresh?settings.sampleRate+' Hz target':'Unavailable'}</strong></span></div><div><SlidersHorizontal size={21}/><span>POSITION FILTER<strong>{pose.estimated?'Inertial estimate':pose.ready?'GNSS aided':'Waiting'}</strong></span></div></div>
      {!native&&<button className="button primary" disabled={busy} onClick={async()=>{if(running){await stopEngine();setRunning(false);setPose(initialPose);}else{await startEngine(true);setRunning(true);}}}>{running?<Square size={16}/>:<Play size={16}/>} {running?'Stop simulation':'Explore simulated motion'}</button>}
      {route&&!route.live&&<button className="text-button" onClick={()=>setRoute(null)}>Clear route preview</button>}
     </div>}
    </section>}
    {active&&!plannerOpen&&<NavigationSummary route={route!} pose={pose} units={settings.units} onExit={()=>void exitNavigation()}/>}
    {active&&rerouteStatus&&<div className="reroute-notice" role="status">{rerouteStatus}</div>}
    <div className="stitch-attribution">© <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">OpenStreetMap</a> contributors</div>
   </div>
   {page==='drives'&&<section className="stitch-page journeys-page">
    <header className="page-heading"><span className="heading-icon"><Route size={23}/></span><div><h1>{selected?'Journey details':'Recorded Journeys'}</h1><p><span className="green-dot"/>{drives.length} local & synced logs</p></div><button aria-label="Open settings" className="quiet-button" onClick={openSettings}><Settings2 size={20}/></button></header>
    {selected?<><button className="text-button" onClick={()=>setSelected(null)}><ArrowLeft size={17}/> All journeys</button>
     <section className="surface"><h2>{selected.title||selected.route||'Journey'}</h2><p className="muted">{new Date(selected.startedAt).toLocaleString()}</p><div className="journey-metrics"><div><span>DISTANCE</span><strong>{distance(selected.distanceMeters)}</strong></div><div><span>DURATION</span><strong>{elapsed(selected.durationSeconds)}</strong></div><div><span>STORAGE</span><strong>{num(selected.sizeBytes/1048576,2)} MB</strong></div></div><p>{selected.phoneModel} · {selected.mount}</p><p>{selected.notes||'No notes recorded.'}</p>
     <label className="field-label">Journey notes<textarea value={selected.notes||''} maxLength={2000} onChange={e=>{const updated={...selected,notes:e.target.value};setSelected(updated);saveDrives(readDrives().map(d=>d.sessionId===updated.sessionId?updated:d));}}/></label>
     {selected.status==='pending'&&settings.apiUrl&&<button className="button primary" disabled={busy} onClick={()=>void upload(selected)}><CloudUpload size={18}/> Upload journey</button>}
     {native&&canUploadJourney(selected.source,cloud.status.connected,cloud.status.consent)&&cloud.status.queue.find(q=>q.sessionId===selected.sessionId)?.state!=='uploaded'&&<button className="button primary" disabled={busy} onClick={async()=>{setBusy(true);try{await Cloud.enqueueTrip({sessionId:selected.sessionId});await cloud.refresh();setToast('Trip queued for private cloud upload.');}catch(e){setToast(errorMessage(e));}finally{setBusy(false);}}}>Upload / retry with Supabase</button>}<small className="muted">{selected.source==='simulation'?'Simulated recording · excluded from physical training':cloudJourneyLabel(cloud.status.queue.find(q=>q.sessionId===selected.sessionId))}</small></section>
     <section className="surface"><h2>Drift & accuracy</h2>{report?<><div className="journey-metrics"><div><span>TRAJECTORY ERROR</span><strong>{num(report.ateMeters,2)} m</strong></div><div><span>DRIFT</span><strong>{num(report.driftPercent,2)}%</strong></div></div><ResponsiveContainer width="100%" height={230}><AreaChart data={report.timestamps.map((time,i)=>({time,drift:report.driftMeters[i]}))}><CartesianGrid stroke="#dce4ee"/><XAxis dataKey="time" unit="s"/><YAxis unit="m"/><Tooltip/><Area dataKey="drift" stroke="#2473ee" fill="#cce0ff"/></AreaChart></ResponsiveContainer></>:<p className="muted">{reportState}</p>}</section>
    </>:<>
     <div className="journey-metrics summary-metrics"><div><span>TOTAL DIST.</span><strong>{distance(totalDistance)}</strong></div><div><span>AVG. DRIFT</span><strong>Unavailable</strong></div><div><span>LOCAL STORAGE</span><strong>{num(totalBytes/1048576,1)} MB</strong></div></div>
     <div className="list-heading"><span>RECENT SESSIONS ({drives.length})</span><button disabled={refreshing} onClick={()=>void refreshDrives()}><RefreshCw size={13} className={refreshing?'spin':''}/>Latest first</button></div>
     {!online&&<p className="offline-note"><Wifi size={14}/> Offline · showing saved journeys</p>}
     {newest.length?newest.map((d,i)=><button className="journey-card" key={d.sessionId} onClick={()=>void showDetail(d)}><span className="journey-number">#{String(newest.length-i).padStart(2,'0')}</span><div className="journey-info"><strong>{d.title||d.route||'Local journey'}</strong><small>{new Date(d.startedAt).toLocaleDateString(undefined,{month:'short',day:'numeric'})} · {elapsed(d.durationSeconds)}</small><span className="mount-tag"><Smartphone size={11}/>{d.mount} mount</span></div><div className="journey-numbers"><strong>{distance(d.distanceMeters)}</strong><span className="saved-badge">{d.source==='simulation'?'SIMULATED':d.status==='pending'?'SAVED':'SYNCED'}</span><small>Details <ChevronRight size={12}/></small></div></button>):<div className="empty-journeys"><Route size={36}/><h2>No journeys yet</h2><p>Start navigation to collect a journey locally. It appears here when navigation ends.</p><button className="button primary" onClick={()=>go('navigate')}>Plan a journey</button></div>}
    </>}
   </section>}
   {page==='sensors'&&<section className="stitch-page sensors-page"><header className="page-heading"><span className="heading-icon"><Radio size={23}/></span><div><h1>Sensors</h1><p>Live device measurements</p></div><button aria-label="Open settings" className="quiet-button" onClick={openSettings}><Settings2 size={20}/></button></header>
    <section className="surface"><h2><ShieldCheck size={19}/> Position & alignment</h2>{diagnostics}</section>
    <section className="surface"><h2><Activity size={19}/> Adaptive learning</h2>{recoveryWarning&&<p role="alert">{recoveryWarning}</p>}<p>Navigation sessions collect IMU and GNSS data locally. Only quality-checked observations can train a candidate model; new weights are evaluated before deployment.</p><div className="recording-status"><span className={'status-dot '+(logging?'green':'')}/>{logging?'Collecting locally':settings.learningEnabled===false?'Collection paused':'Ready for the next navigation'}</div><p className="muted">{logging?elapsed(pose.elapsed)+' · '+num(pose.bytes/1048576,2)+' MB on device':cloud.status.consent?'Cloud contribution enabled · uploads follow your network preference.':'Cloud contribution is off; recordings stay on this phone.'}</p><button className="button secondary" disabled={busy} onClick={async()=>{if(settings.learningEnabled===false)setSettings({...settings,learningEnabled:true});else if(await changeRecording(false))setSettings({...settings,learningEnabled:false});}}>{settings.learningEnabled===false?'Enable local collection':'Pause local collection'}</button>{logging&&!active&&<button className="button secondary" disabled={busy} onClick={()=>void changeRecording(false)}>Finish recovered recording</button>}</section>
    <section className="surface"><h2>Model status</h2><p>{pose.modelStatus||'Live model status unavailable'}</p>{modelVersions.map(v=><p className="model-line" key={v}><span className="green-dot"/>{v}</p>)}<div className="telemetry-list">{[['GNSS',pose.gpsTrust],['Physics',pose.physicsTrust],['Map match',pose.mapTrust],['AI speed',pose.aiTrust]].map(([name,value])=><div key={String(name)}><span>{name} advisor trust</span><strong>{pose.ready?num(Number(value)*100)+'%':'Unavailable'}</strong></div>)}</div><p className="muted">Advisor trust values are diagnostics, not measured positioning accuracy or percentages that must sum to 100.</p></section>
    {native&&<div className="sensor-actions"><button className="button secondary" onClick={()=>void SensorPlugin.shareDiagnostics().catch(e=>setToast(errorMessage(e)))}><Download size={18}/> Export diagnostics & latest training log</button><button className="text-button" onClick={()=>void DrEngine.backgroundSettings()}>Location and background permissions</button></div>}
   </section>}
   {!plannerOpen&&!(active&&page==='navigate')&&<nav className="stitch-nav" aria-label="Main navigation">{([{id:'navigate',label:'Navigate',icon:Navigation},{id:'drives',label:'Journeys',icon:Route},{id:'sensors',label:'Sensors',icon:Radio}] as const).map(item=><button key={item.id} aria-current={page===item.id?'page':undefined} className={page===item.id?'active':''} onClick={()=>go(item.id)}><item.icon size={19}/><span>{item.label}</span></button>)}</nav>}
   <dialog ref={dialog} className="stitch-settings" aria-labelledby="settings-title" onCancel={()=>setSettingsOpen(false)} onClick={e=>{if(e.target===dialog.current)setSettingsOpen(false);}}>
    <div className="settings-content"><header className="settings-heading"><span className="heading-icon"><Settings2 size={21}/></span><div><span className="eyebrow">PREFERENCES</span><h2 id="settings-title">Settings</h2></div><button aria-label="Close settings" onClick={()=>setSettingsOpen(false)}><X size={19}/></button></header>
    <section className="settings-box"><span className="field-caption">PROFILE DETAILS</span><div className="neutral-profile"><span className="profile-avatar"><Navigation size={22}/></span><div><strong>On-device profile</strong><small>No account connected</small></div></div><div className="profile-device"><span>Primary device</span><strong>{native?'This Android phone':'Browser preview'}</strong></div></section>
    <section className="settings-box"><span className="field-caption">PHONE MOUNT SETUP</span><div className="segments">{(['dashboard','windshield','console'] as const).map(m=><button key={m} disabled={logging} className={draft.mount===m?'selected':''} onClick={()=>setDraft({...draft,mount:m})}>{m.charAt(0).toUpperCase()+m.slice(1)}</button>)}</div><small>{logging?'Pause collection before changing the mount.':pose.fusionStatus||'Alignment status unavailable'}</small></section>
    <section className="settings-box"><span className="field-caption">DISTANCE UNITS</span><div className="segments">{(['km','mi'] as const).map(u=><button key={u} className={draft.units===u?'selected':''} onClick={()=>setDraft({...draft,units:u})}>{u==='km'?'Kilometers (km)':'Miles (mi)'}</button>)}</div></section>
    <section className="settings-box"><span className="field-caption">APPEARANCE THEME</span><div className="segments">{(['light','dark','system'] as const).map(t=><button key={t} className={draft.theme===t?'selected':''} onClick={()=>setDraft({...draft,theme:t})}>{t.charAt(0).toUpperCase()+t.slice(1)}</button>)}</div></section>
    <CloudSettings status={cloud.status} refresh={cloud.refresh} error={cloud.error} canApply={!route?.live&&!logging&&!busy} onApply={async()=>{if(routeRef.current?.live||recorder.current?.active)throw new Error('Finish navigation and recording first');setBusy(true);setRunning(false);try{await stopEngine();await Cloud.applyPendingModels();}finally{try{await ensureNativeEngine();setRunning(true);}finally{setBusy(false);}}}}/><details className="advanced-settings"><summary>Collection & legacy server</summary>
     <label className="checkbox-label"><input type="checkbox" checked={draft.learningEnabled!==false} onChange={e=>setDraft({...draft,learningEnabled:e.target.checked})}/>Collect navigation data locally for learning</label>
     <label className="field-label">Capture rate<select disabled={logging} value={draft.sampleRate} onChange={e=>setDraft({...draft,sampleRate:Number(e.target.value) as 100|200})}><option value={100}>100 Hz target</option><option value={200}>200 Hz target</option></select></label>
     <label className="field-label">Backend API address<input value={draft.apiUrl} placeholder="https://your-api.example.com" onChange={e=>setDraft({...draft,apiUrl:e.target.value})}/></label>
     <label className="field-label">Workspace API key<input type="password" autoComplete="off" value={draft.apiKey} onChange={e=>setDraft({...draft,apiKey:e.target.value})}/></label>
     <p className="muted">Save preferences before testing the connection. Uploads are optional and initiated from journey details.</p>
     <button className="button secondary" onClick={async()=>{try{await api.health();await client().get('/api/drives?limit=1');setOnline(true);setToast('API and database connected.');}catch(e){setOnline(false);setToast(errorMessage(e));}}}><Wifi size={16}/>Test saved connection</button>
     <button className="button secondary" disabled={busy} onClick={async()=>{setBusy(true);try{setToast((await updateModels()).join(' · '));if(native){const r=await ModelPlugin.checkForUpdate();setModelVersions(r.models.map(m=>m.name+' · '+m.version));}}catch(e){setToast(errorMessage(e));}finally{setBusy(false);}}}><Download size={16}/>Check legacy server models</button>
     <button className="text-button" onClick={()=>{setSettingsOpen(false);setOnboarding(true);}}>Review permissions</button>
    </details>
    <button className="button primary save-preferences" disabled={busy} onClick={()=>void savePreferences()}><Check size={16}/>Save Preferences</button></div>
   </dialog>
   {onboarding&&<div className="stitch-onboarding"><section className="surface"><span className="heading-icon"><Navigation size={30}/></span><h1>OffRoute</h1><h2>Your map, beyond the signal.</h2><p>Keep your phone mounted. OffRoute uses location and motion sensors to estimate movement when GPS is limited.</p><p>{native?'Location permission starts the engine. Android shows a notification while tracking.':'This browser preview can show simulated motion. Live navigation and offline route calculation run on Android.'}</p><button className="button primary" disabled={busy} onClick={async()=>{try{if(native)await DrEngine.requestPermissions();localStorage.setItem('idr-onboarded','true');setOnboarding(false);}catch(e){setToast(errorMessage(e));}}}>{native?'Enable permissions':'Explore the preview'}</button></section></div>}
   {toast&&<div className="stitch-toast" role="status"><Info size={18}/><span>{toast}</span><button aria-label="Dismiss notification" onClick={()=>setToast('')}><X size={17}/></button></div>}
 </div>;
}

