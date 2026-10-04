import { Capacitor, registerPlugin } from '@capacitor/core';
import type { Pose } from './types';
export type Place = { id: string; name: string; lat: number; lon: number; type: string; address: string; aliases?: string };
export type Endpoint = { kind: 'current' } | { kind: 'place'; place: Place };
export type TravelMode = 'car' | 'motorcycle';
export type DirectionStep={text:string;sign:number;lat:number;lon:number;distance:number;duration:number};
export type RoadRoute = { coordinates: [number, number][]; distance: number; duration: number; start: Place; destination: Place; mode: TravelMode; live: boolean; steps?:DirectionStep[] };
const Directions = registerPlugin<{ route(input: { fromLat: number; fromLon: number; toLat: number; toLon: number; mode:TravelMode }): Promise<Pick<RoadRoute,'coordinates'|'distance'|'duration'|'steps'>> }>('Directions');
let placeData: Promise<Place[]> | undefined;
export function loadDestinationPlaces() {
  return placeData ??= fetch('/maps/destination-places.json').then(r => {
    if (!r.ok) throw new Error('Offline places could not be loaded. Close search and try again.');
    return r.json() as Promise<Place[]>;
  }).catch(error => { placeData = undefined; throw error; });
}
export function endpointName(value: Endpoint | null) { return value?.kind === 'current' ? 'Your current location' : value?.place.name || ''; }
export function resolveEndpoint(value: Endpoint | null, pose: Pose): Place | null {
  if (!value) return null;
  if (value.kind === 'place') return value.place;
  if (!pose.ready || (pose.positionFresh===false&&!pose.estimated) || pose.source !== 'device' || !Number.isFinite(pose.lat) || !Number.isFinite(pose.lon)) return null;
  return { id:'current',name:'Your current location',lat:pose.lat,lon:pose.lon,type:'Current location',address:'Live device position' };
}
export function distanceKm(a: {lat:number;lon:number}, b: {lat:number;lon:number}) {
  const rad=Math.PI/180, dLat=(b.lat-a.lat)*rad, dLon=(b.lon-a.lon)*rad;
  return 12742*Math.asin(Math.min(1,Math.sqrt(Math.sin(dLat/2)**2+Math.cos(a.lat*rad)*Math.cos(b.lat*rad)*Math.sin(dLon/2)**2)));
}
const normalize=(s:string)=>s.normalize('NFKC').toLocaleLowerCase().trim();
const searchable=new WeakMap<Place,{name:string;text:string}>();
function indexed(p:Place) {
  let entry=searchable.get(p);
  if(!entry){entry={name:normalize(p.name),text:normalize(`${p.name} ${p.aliases||''} ${p.address} ${p.type}`)};searchable.set(p,entry);}
  return entry;
}
export function searchPlaces(places: Place[], query:string, near?: {lat:number;lon:number}): Place[] {
  const q=normalize(query), words=q.split(/\s+/).filter(Boolean);
  return places.filter(p=>words.every(w=>indexed(p).text.includes(w)))
    .map(p=>({p,score:(indexed(p).name===q ? 0 : indexed(p).name.startsWith(q) ? 1 : 2),distance:near?distanceKm(near,p):0}))
    .sort((a,b)=>a.score-b.score || a.distance-b.distance || a.p.name.localeCompare(b.p.name)).slice(0,12).map(x=>x.p);
}
export async function calculateRoute(start: Place, destination: Place, mode:TravelMode='car'): Promise<RoadRoute> {
  if (distanceKm(start,destination)<.005) throw new Error('Choose a destination different from your starting point.');
  if (!Capacitor.isNativePlatform()) throw new Error('Offline road directions are available in the Android app. You can explore and select places in this browser preview.');
  const result=await Directions.route({fromLat:start.lat,fromLon:start.lon,toLat:destination.lat,toLon:destination.lon,mode});
  if (!result.coordinates || result.coordinates.length<2 || result.coordinates.some(p=>p.length!==2 || !p.every(Number.isFinite) || Math.abs(p[0])>180 || Math.abs(p[1])>85.051129) || !Number.isFinite(result.distance) || !Number.isFinite(result.duration))
    throw new Error('The road route could not be displayed. Please choose another place.');
  return {...result,start,destination,mode,live:start.id==='current'};
}
