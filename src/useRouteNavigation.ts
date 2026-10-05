import {useEffect,useRef,useState} from 'react';
import {calculateRoute,resolveEndpoint,type RoadRoute} from './directions';
import {projectOnRoute,RerouteGate} from './routeProgress';
import type {Pose} from './types';
export function useRouteNavigation(pose:Pose,route:RoadRoute|null,onRoute:(route:RoadRoute)=>void) {
  const gate=useRef(new RerouteGate()),epoch=useRef(0),pending=useRef(false);
  const latest=useRef(pose); latest.current=pose;
  const [notice,setNotice]=useState({text:'',at:0});
  const identity=route ? route.destination.id+':'+route.mode+':'+route.live : '';
  // Preserve cooldown when a reroute replaces geometry for the same destination.
  useEffect(()=>{epoch.current++;gate.current=new RerouteGate();setNotice({text:'',at:0});return()=>{epoch.current++;};},[identity]);
  useEffect(()=>{
    if(!notice.text)return;
    const timer=setTimeout(()=>setNotice({text:'',at:0}),12000);
    return()=>clearTimeout(timer);
  },[notice.at]);
  useEffect(()=>{
    if(!route?.live)return;
    if(pending.current){
      if(pose.ready&&(pose.estimated||pose.health==='BLACKOUT'||pose.positionFresh===false)){
        // Preserve outage history and discard the pre-outage request even if GPS returns first.
        gate.current.shouldReroute(pose,Infinity,Date.now());epoch.current++;
        if(notice.text)setNotice({text:'',at:0});
      }
      return;
    }
    const projection=projectOnRoute(pose,route.coordinates);
    const reliable=!pose.estimated&&pose.health==='HEALTHY'&&pose.positionFresh!==false;
    if(reliable&&projection.distance<=Math.max(25,2*(pose.gpsAccuracy??pose.accuracy))&&notice.text){
      setNotice({text:'',at:0});
    }
    if(!gate.current.shouldReroute(pose,projection.distance,Date.now()))return;
    const start=resolveEndpoint({kind:'current'},pose);if(!start)return;
    const token=epoch.current;pending.current=true;setNotice({text:'Updating your route…',at:Date.now()});
    void calculateRoute(start,route.destination,route.mode).then(result=>{
      const p=latest.current;
      if(token!==epoch.current||p.estimated||p.health!=='HEALTHY'||p.positionFresh===false||Math.max(p.gpsAccuracy??p.accuracy,p.accuracy)>25)return;
      onRoute(result);setNotice({text:'Route updated',at:Date.now()});
    }).catch(()=>{
      if(token===epoch.current)setNotice({text:'Reroute unavailable. Previous route retained; follow road signs.',at:Date.now()});
    }).finally(()=>{pending.current=false;});
  },[pose,route,onRoute]);
  return pose.estimated||pose.health==='BLACKOUT' ? '' : notice.text;
}

