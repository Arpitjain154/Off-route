import { useEffect, useRef, useState, useMemo } from 'react';
import Map, { Marker, Source, Layer, type MapRef } from 'react-map-gl/maplibre';
import { setWorkerUrl, type StyleSpecification } from 'maplibre-gl';
import mapWorkerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url';
import type { Pose } from './types';
import type { RoadRoute } from './directions';
import { safeNavigationPose } from './navigationPose';
import { initialPose, reportEngineDiagnostic } from './engine';
import {projectOnRoute} from './routeProgress';
import {loadDestinationPlaces,type Place} from './directions';
import './map-view.css';
// Explicitly bundle the worker and its imports for both Vite and offline Capacitor assets.
setWorkerUrl(mapWorkerUrl);
const style: StyleSpecification = {
  version: 8,
  sources: {
    bhopal: {
      type: 'vector',
      tiles: [
        new URL('/maps/tiles/{z}/{x}/{y}.pbf?v=2', window.location.origin).href
          .replaceAll('%7B', '{')
          .replaceAll('%7D', '}'),
      ],
      minzoom: 11,
      maxzoom: 16,
      bounds: [77.38, 23.22, 77.43, 23.27],
    },
  },
  layers: [
    { id: 'ground', type: 'background', paint: { 'background-color': '#111a20' } },
    {
      id: 'land',
      type: 'fill',
      source: 'bhopal',
      'source-layer': 'bhopal',
      filter: ['==', ['get', 'kind'], 'land'],
      paint: {
        'fill-color': ['match', ['get', 'type'], 'water', '#172c38', 'park', '#172b27', '#1a242a'],
        'fill-opacity': 0.9,
      },
    },
    {
      id: 'buildings',
      type: 'fill',
      source: 'bhopal',
      'source-layer': 'bhopal',
      filter: ['==', ['get', 'kind'], 'building'],
      paint: { 'fill-color': '#202c32', 'fill-outline-color': '#28353c' },
    },
    {
      id: 'road-casing',
      type: 'line',
      source: 'bhopal',
      'source-layer': 'bhopal',
      filter: ['==', ['get', 'kind'], 'road'],
      paint: {
        'line-color': '#0d161c',
        'line-width': ['interpolate', ['linear'], ['zoom'], 12, 2, 16, 12],
      },
    },
    {
      id: 'roads',
      type: 'line',
      source: 'bhopal',
      'source-layer': 'bhopal',
      filter: ['==', ['get', 'kind'], 'road'],
      paint: {
        'line-color': [
          'match',
          ['get', 'type'],
          'primary',
          '#526068',
          'secondary',
          '#46535b',
          'tertiary',
          '#394951',
          '#2d3b43',
        ],
        'line-width': ['interpolate', ['linear'], ['zoom'], 12, 1, 16, 7],
      },
    },
  ],
};
style.sources['madhya-pradesh'] = {
  type: 'vector',
  tiles: [new URL('/maps/mp-tiles/{z}/{x}/{y}.pbf', window.location.origin).href.replaceAll('%7B','{').replaceAll('%7D','}')],
  minzoom: 5, maxzoom: 13, bounds: [74.029382,21.0706885,82.8126116,26.8695616],
};
style.layers.splice(1, 0, ...style.layers.slice(1).filter(l=>l.id!=='buildings').map(l=>({
  ...l, id: `mp-${l.id}`, source: 'madhya-pradesh',
})));
for(const source of ['madhya-pradesh','bhopal'])style.layers.push({
  id:`${source}-road-names`,type:'symbol',source,'source-layer':'bhopal',minzoom:11,
  filter:['all',['==',['get','kind'],'road'],['!=',['coalesce',['get','name'],''],'']],
  layout:{'symbol-placement':'line','symbol-spacing':350,'text-field':['get','name'],
    'text-font':['Noto Sans','Arial','sans-serif'],'text-size':['interpolate',['linear'],['zoom'],11,10,16,13],
    'text-max-angle':35,'text-padding':8,'text-allow-overlap':false,'text-keep-upright':true},
  paint:{'text-color':'#c9d6de','text-halo-color':'#111a20','text-halo-width':1.5}
});
// The same bundled vector sources are restyled; no remote map or font service is required.
const lightStyle: StyleSpecification = JSON.parse(JSON.stringify(style));
for (const layer of lightStyle.layers) {
  if (layer.type === 'background') layer.paint = { 'background-color': '#f1f4f6' };
  else if (layer.type === 'fill' && layer.id.includes('land')) layer.paint = { 'fill-color': ['match', ['get', 'type'], 'water', '#deebf4', 'park', '#e6eedf', '#f0f2ee'], 'fill-opacity': .92 };
  else if (layer.type === 'fill') layer.paint = { 'fill-color': '#e8eced', 'fill-outline-color': '#dde3e6' };
  else if (layer.type === 'line' && layer.id.includes('casing')) layer.paint = { ...layer.paint, 'line-color': '#d7dfe4' };
  else if (layer.type === 'line') layer.paint = { ...layer.paint, 'line-color': ['match', ['get', 'type'], 'primary', '#ffffff', 'secondary', '#ffffff', 'tertiary', '#fcfcfc', '#f9fafb'] };
  else if (layer.type === 'symbol') layer.paint = { ...layer.paint, 'text-color': '#657584', 'text-halo-color': '#f3f6f8', 'text-halo-width': 1.5 };
}

/** Accuracy is a radius in metres, drawn in map coordinates so it scales with zoom. */
export function positionAccuracyFeature(pose: Pick<Pose, 'lat' | 'lon' | 'ready' | 'accuracy'>): GeoJSON.FeatureCollection<GeoJSON.Polygon> {
  const empty: GeoJSON.FeatureCollection<GeoJSON.Polygon> = { type: 'FeatureCollection', features: [] };
  if (!pose.ready || !Number.isFinite(pose.lat) || !Number.isFinite(pose.lon) || !Number.isFinite(pose.accuracy) || pose.accuracy <= 0) return empty;
  const radius = pose.accuracy / 6371008.8, lat = pose.lat * Math.PI / 180, lon = pose.lon * Math.PI / 180;
  const coordinates: number[][] = [];
  for (let i = 0; i < 64; i++) {
    const bearing = i / 64 * Math.PI * 2;
    const nextLat = Math.asin(Math.sin(lat) * Math.cos(radius) + Math.cos(lat) * Math.sin(radius) * Math.cos(bearing));
    const nextLon = lon + Math.atan2(Math.sin(bearing) * Math.sin(radius) * Math.cos(lat), Math.cos(radius) - Math.sin(lat) * Math.sin(nextLat));
    coordinates.push([((nextLon * 180 / Math.PI + 540) % 360) - 180, nextLat * 180 / Math.PI]);
  }
  coordinates.push([...coordinates[0]]);
  return { type: 'FeatureCollection', features: [{ type: 'Feature', properties: { radiusMeters: pose.accuracy }, geometry: { type: 'Polygon', coordinates: [coordinates] } }] };
}
export default function MapView({
  pose,
  follow,
  recenter,
  route,
  onMove,
  destinationRoute,
  theme = 'light',
}: {
  pose: Pose;
  follow: boolean;
  recenter: number;
  route: boolean;
  onMove: () => void;
  destinationRoute?: RoadRoute | null;
  theme?: 'light' | 'dark';
}) {
  const ref = useRef<MapRef>(null);
  const target = useRef(safeNavigationPose(pose) || initialPose),
    current = useRef(target.current);
  const [smooth, setSmooth] = useState(target.current);
  const reportedError = useRef(false);
  const report = (error: unknown) => {
    if (!reportedError.current) { reportedError.current = true; reportEngineDiagnostic(error); }
  };
  useEffect(()=>{
    if(!destinationRoute||destinationRoute.live)return;
    const coordinates=destinationRoute.coordinates;
    if(coordinates.length<2)return;
    const min=[Infinity,Infinity],max=[-Infinity,-Infinity];
    for(const p of coordinates){min[0]=Math.min(min[0],p[0]);min[1]=Math.min(min[1],p[1]);max[0]=Math.max(max[0],p[0]);max[1]=Math.max(max[1],p[1]);}
    // Camera fitting only needs the map instance. Waiting for the map's load
    // event also waits for tiles, delaying the route overview on a cold launch.
    let frame: number;
    const fit=()=>{
      if(!ref.current){frame=requestAnimationFrame(fit);return;}
      try {
        // MapLibre adds fit padding to the camera's persistent follow padding.
        // Clear that padding first, or a phone-height map cannot fit any route.
        const map=ref.current;
        map.getMap().setPadding({top:0,bottom:0,left:0,right:0});
        const height=map.getContainer().clientHeight, width=map.getContainer().clientWidth;
        map.fitBounds([[min[0],min[1]],[max[0],max[1]]],{padding:{top:Math.min(100,height*.2),bottom:Math.min(210,height*.4),left:Math.min(50,width*.15),right:Math.min(60,width*.18)},bearing:0,duration:600,maxZoom:16});
      }
      catch(error){report(error);}
    };
    frame=requestAnimationFrame(fit);
    return ()=>cancelAnimationFrame(frame);
  },[destinationRoute]);
  const [places, setPlaces] = useState<Place[]>([]);
  useEffect(()=>{let active=true;loadDestinationPlaces().then(p=>{if(active)setPlaces(p);}).catch(report);return()=>{active=false;};},[]);
  const placeFeatures=useMemo<GeoJSON.FeatureCollection>(()=>({type:'FeatureCollection',features:places.filter(p=>p.type!=='county').map(p=>({type:'Feature',properties:{name:p.name,type:p.type,rank:p.type==='city'?0:p.type==='town'?1:2},geometry:{type:'Point',coordinates:[p.lon,p.lat]}}))}),[places]);
  const [track, setTrack] = useState<GeoJSON.Feature<GeoJSON.LineString>>({
    type: 'Feature',
    properties: {},
    geometry: { type: 'LineString', coordinates: [] },
  });
  useEffect(() => {
    const safe = safeNavigationPose(pose);
    if (!safe) { report(new Error('Rejected invalid map coordinates from native engine')); return; }
    // First real fix must not animate from the offline preview's Bhopal center.
    if (safe.ready && !target.current.ready) current.current = safe;
    target.current = safe;
  }, [pose]);
  useEffect(() => {
    let frame: number;
    let prev = performance.now();
    let lastTrack = 0;
    let lastCamera = 0;
    const tick = (now: number) => {
      frame = requestAnimationFrame(tick);
      if (document.visibilityState === 'hidden' || now - prev < 32) return;
      try {
      const dt = Math.min((now - prev) / 1000, 0.1);
      prev = now;
      const a = 1 - Math.exp(-dt * 9),
        p = current.current,
        q = target.current;
      const desiredHeading = q.headingReady === false ? 0 : q.heading;
      const heading = (p.heading + (((desiredHeading - p.heading + 540) % 360) - 180) * a + 360) % 360;
      const next = {
        ...q,
        lat: p.lat + (q.lat - p.lat) * a,
        lon: p.lon + (q.lon - p.lon) * a,
        heading,
      };
      current.current = next;
      setSmooth(next);
      if (follow && q.ready && now - lastCamera >= 100) {
        lastCamera = now;
        ref.current?.jumpTo({
          center: [next.lon, next.lat],
          bearing: 0,
          padding: { top: 140, bottom: 220, left: 0, right: 0 },
        });
      }
      if (route && !destinationRoute?.live && (q.logging||q.source==='simulation') && q.ready && (q.positionFresh!==false||q.estimated) && now - lastTrack > 1500) {
        lastTrack = now;
        setTrack((old) => ({
          ...old,
          geometry: {
            ...old.geometry,
            coordinates: [...old.geometry.coordinates.slice(-1800), [q.lon, q.lat]],
          },
        }));
      }
      } catch (error) { report(error); }
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [follow, route, destinationRoute?.live]);
  useEffect(() => {
    try {
    if (recenter)
      ref.current?.jumpTo({
        center: [target.current.lon, target.current.lat],
        zoom: 15,
        bearing: 0,
      });
    } catch (error) { report(error); }
  }, [recenter]);
  const routeFeature=useMemo<GeoJSON.Feature<GeoJSON.LineString>|null>(()=>{
    if(!destinationRoute)return null;
    const reliable=pose.ready&&Number.isFinite(pose.accuracy)&&pose.accuracy<=50&&(pose.estimated?pose.imuFresh!==false&&pose.trackingAvailable!==false:pose.positionFresh!==false&&pose.health!=='BLACKOUT');
    const projection=destinationRoute.live&&reliable?projectOnRoute(pose,destinationRoute.coordinates):null;
    const coordinates=projection&&projection.distance<Math.max(25,Math.min(50,2*pose.accuracy))?
      [projection.point,...destinationRoute.coordinates.slice(projection.index+1)]:destinationRoute.coordinates;
    return {type:'Feature',properties:{},geometry:{type:'LineString',coordinates}};
  },[destinationRoute,pose]);
  const accuracyFeature=useMemo(()=>positionAccuracyFeature({...pose,lat:smooth.lat,lon:smooth.lon}),[pose.ready,pose.accuracy,smooth.lat,smooth.lon]);
  return (
    <Map
      ref={ref}
      initialViewState={{ longitude: 77.4029, latitude: 23.2425, zoom: 14.6, pitch: 0 }}
      mapStyle={theme === 'dark' ? style : lightStyle}
      localIdeographFontFamily="sans-serif"
      onError={(e)=>report(e.error)}
      attributionControl={false}
      onDragStart={onMove}
      maxZoom={18}
      minZoom={5}
    >
      <button type="button" className="map-overview" title="Show offline map coverage" aria-label="Show Madhya Pradesh offline map"
        onClick={()=>{onMove();ref.current?.fitBounds([[74.029382,21.0706885],[82.8126116,26.8695616]],{padding:50,duration:0});}}>
        Offline map · Madhya Pradesh
      </button>
      <Source id="position-accuracy" type="geojson" data={accuracyFeature}>
        <Layer id="position-accuracy-fill" type="fill" paint={{'fill-color':pose.estimated?'#edaa32':'#3e91e8','fill-opacity':.1}}/>
        <Layer id="position-accuracy-outline" type="line" paint={{'line-color':pose.estimated?'#d59522':'#5898df','line-width':1.2,'line-opacity':.7,'line-dasharray':[3,3]}}/>
      </Source>
      {!destinationRoute?.live&&<Source id="driven-track" type="geojson" data={track}>
        <Layer
          id="track-glow"
          type="line"
          paint={{ 'line-color': '#3d9dff', 'line-width': 13, 'line-opacity': 0.14 }}
        />
        <Layer id="track" type="line" paint={{ 'line-color': '#56aaff', 'line-width': 4 }} />
      </Source>}
      {destinationRoute&&<>
        <Source id="destination-route" type="geojson" data={routeFeature!}>
          <Layer id="destination-route-outline" type="line" layout={{'line-cap':'round','line-join':'round'}} paint={{'line-color':theme==='dark'?'#183e6b':'#c1d7f3','line-width':11}}/>
          <Layer id="destination-route-line" type="line" layout={{'line-cap':'round','line-join':'round'}} paint={{'line-color':'#287ee6','line-width':7}}/>
          <Layer id="destination-route-center" type="line" layout={{'line-cap':'round','line-join':'round'}} paint={{'line-color':'#ffffff','line-width':1.7,'line-dasharray':[3,5],'line-opacity':.9}}/>
        </Source>
        {[destinationRoute.coordinates[0],destinationRoute.coordinates[destinationRoute.coordinates.length-1]].map((p,i)=>destinationRoute.live&&i===0?null:<Marker key={i} longitude={p[0]} latitude={p[1]}><span className={'destination-pin '+(i?'end':'')} aria-label={i?'Route destination':'Route starting point'}>{i?'B':'A'}</span></Marker>)}
      </>}
      <Source id="offline-places" type="geojson" data={placeFeatures}>
        <Layer id="city-labels" type="symbol" minzoom={6} maxzoom={14} filter={['in',['get','type'],['literal',['city','town']]]}
          layout={{'text-field':['get','name'],'text-font':['Noto Sans','Arial','sans-serif'],'text-size':13,'text-padding':12,'symbol-sort-key':['get','rank'],'text-allow-overlap':false}}
          paint={{'text-color':theme==='dark'?'#d2dfe7':'#697783','text-halo-color':theme==='dark'?'#111a20':'#f4f6f8','text-halo-width':2}}/>
        <Layer id="poi-labels" type="symbol" minzoom={14} filter={['!',['in',['get','type'],['literal',['city','town','village']]]]}
          layout={{'text-field':['get','name'],'text-font':['Noto Sans','Arial','sans-serif'],'text-size':['interpolate',['linear'],['zoom'],14,10,18,13],'text-max-width':12,'text-padding':10,'text-allow-overlap':false,'symbol-sort-key':['get','rank']}}
          paint={{'text-color':theme==='dark'?'#b4d6c9':'#667b70','text-halo-color':theme==='dark'?'#111a20':'#f4f6f8','text-halo-width':1.5}}/>
      </Source>
      {pose.ready && <Marker
        longitude={smooth.lon}
        latitude={smooth.lat}
        anchor="center"
        rotation={smooth.heading}
        rotationAlignment="map"
      >
        <div data-latitude={smooth.lat} data-longitude={smooth.lon} data-heading={smooth.heading} aria-label={pose.estimated?'Sensor-estimated position':pose.positionFresh===false?'Last known position':'Live position marker'} className={'stitch-position ' + (pose.positionFresh===false&&!pose.estimated ? 'waiting' : '')}>
          {pose.headingReady !== false && <span className="stitch-heading-cone" aria-hidden="true" />}
          <div className="stitch-position-arrow">
            {pose.headingReady===false?<svg viewBox="0 0 32 36"><circle cx="16" cy="18" r="7" fill="white"/></svg>:<svg viewBox="0 0 32 36">
              <path d="M16 3 28 31 16 25 4 31Z" fill="white" />
            </svg>}
          </div>
        </div>
      </Marker>}
    </Map>
  );
}
