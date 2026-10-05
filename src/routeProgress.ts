import type { Pose } from './types';
import type {RoadRoute} from './directions';
const metres=(a:[number,number],b:[number,number])=>Math.hypot((a[0]-b[0])*111320*Math.cos((a[1]+b[1])*Math.PI/360),(a[1]-b[1])*111320);
export function routeGuidance(route:RoadRoute,pose:{lat:number;lon:number}) {
 const projection=projectOnRoute(pose,route.coordinates),lengths=[0];
 for(let i=1;i<route.coordinates.length;i++)lengths.push(lengths[i-1]+metres(route.coordinates[i-1],route.coordinates[i]));
 const travelled=lengths[projection.index]+metres(route.coordinates[projection.index],projection.point);
 const total=lengths.at(-1)||1,remaining=Math.max(0,total-travelled);
 let offset=0,seconds=0,toTurn=remaining,instruction='Continue to your destination',found=false,nextInstruction: string | undefined;
 const progress=travelled/total*route.distance;
 for(const step of route.steps||[]) {
  if(found&&!nextInstruction)nextInstruction=step.text;
  if(!found&&offset>progress+8){found=true;toTurn=Math.max(0,offset-progress);instruction=step.text;}
  const fraction=step.distance>0?Math.max(0,Math.min(1,(offset+step.distance-progress)/step.distance)):0;
  seconds+=step.duration*fraction;offset+=step.distance;
 }
 if(!route.steps?.length)seconds=route.duration*remaining/total;
 return {remaining,toTurn,seconds,instruction,nextInstruction,offRoute:projection.distance};
}
export function projectOnRoute(point:{lat:number;lon:number},coordinates:[number,number][]) {
  const east=111320*Math.cos(point.lat*Math.PI/180),north=111320;
  let best={distance:Infinity,index:0,point:coordinates[0] as [number,number]};
  for(let i=0;i<coordinates.length-1;i++) {
    const a=coordinates[i],b=coordinates[i+1],ax=(a[0]-point.lon)*east,ay=(a[1]-point.lat)*north;
    const dx=(b[0]-a[0])*east,dy=(b[1]-a[1])*north;
    const t=Math.max(0,Math.min(1,-(ax*dx+ay*dy)/(dx*dx+dy*dy||1)));
    const distance=Math.hypot(ax+t*dx,ay+t*dy);
    if(distance<best.distance)best={distance,index:i,point:[a[0]+t*(b[0]-a[0]),a[1]+t*(b[1]-a[1])]};
  }
  return best;
}
/** Decisions consume distinct GPS fixes, not repeated UI polling of one fix. */
export class RerouteGate {
  private lastFix=-1;private offSince=0;private count=0;private lastRequest=-Infinity;
  private recovering=false;private lastRecoveryFixAt:number|null=null;private onSince:number|null=null;
  shouldReroute(pose:Pose,distance:number,now:number) {
    if(pose.ready&&(pose.estimated||pose.health==='BLACKOUT'||pose.positionFresh===false))this.recovering=true;
    const precision=Math.max(pose.gpsAccuracy??pose.accuracy,pose.accuracy);
    if((pose.gpsAccuracy!==undefined&&(!Number.isFinite(pose.gpsAccuracy)||pose.gpsAccuracy<0))||pose.accuracy<0||!pose.ready||pose.estimated||pose.positionFresh===false||pose.health!=='HEALTHY'||!Number.isFinite(precision)||precision<0||precision>25||(pose.gpsAge!==undefined&&pose.gpsAge>2.5)||!Number.isFinite(distance)||pose.speedAvailable===false){this.offSince=0;this.count=0;this.onSince=null;this.lastRecoveryFixAt=null;return false;}
    const fix=pose.fixTime??now;
    if(fix===this.lastFix)return false;this.lastFix=fix;
    const healthyThreshold=Math.max(25,2*precision);
    if(this.recovering){
      // A suspended UI or missing fixes cannot establish continuous GPS evidence.
      if(this.lastRecoveryFixAt!==null&&(now-this.lastRecoveryFixAt>2500||now<this.lastRecoveryFixAt)){
        this.offSince=0;this.count=0;this.onSince=null;
      }
      this.lastRecoveryFixAt=now;
      if(distance<=healthyThreshold){
        if(this.onSince===null)this.onSince=now;
        if(now-this.onSince>10000){this.recovering=false;this.lastRecoveryFixAt=null;}
        this.offSince=0;this.count=0;return false;
      }
      this.onSince=null;
    }
    if(distance<=Math.max(this.recovering?40:25,2*precision)||pose.stationary){this.offSince=0;this.count=0;return false;}
    if(!this.count)this.offSince=now;
    this.count++;
    const sustained=this.recovering?now-this.offSince>10000:now-this.offSince>=2500;
    if(this.count<3||!sustained||now-this.lastRequest<10000)return false;
    this.lastRequest=now;this.count=0;this.offSince=0;return true;
  }
}
