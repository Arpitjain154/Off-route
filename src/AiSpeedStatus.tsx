import type {Pose} from './types';
export default function AiSpeedStatus({pose,units}:{pose:Pose;units:'km'|'mi'}){
 const valid=pose.aiSpeedAvailable===true&&Number.isFinite(pose.aiSpeedEstimate)&&pose.aiSpeedEstimate!>=0;
 const value=valid?(pose.aiSpeedEstimate!*(units==='mi'?2.236936:3.6)).toFixed(1)+' '+(units==='mi'?'mph':'km/h'):'Unavailable';
 return <><div className="telemetry-list"><div><span>AI speed estimate (experimental)</span><strong>{value}</strong></div></div><p className="muted">{!valid?'Waiting for fresh motion samples. ':''}{pose.aiSpeedValidated?pose.aiFusionActive?'ESKF speed corrections active.':pose.aiTrust>0?'Calibrated model; awaiting an accepted ESKF measurement.':'Model loaded; corrections await calibration.':'Diagnostic output only  -  phone validation pending; not used for navigation.'}</p></>;
}
