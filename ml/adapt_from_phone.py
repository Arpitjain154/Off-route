"""Import native training logs over authorized USB and evaluate a candidate locally.
Never clears phone files, uploads data, or installs model weights.
"""
import argparse,io,json,subprocess,tarfile,uuid
from pathlib import Path
from phone_learning import build_dataset,train
import numpy as np

def main():
 p=argparse.ArgumentParser();p.add_argument('--adb',default=str(Path.home()/'AppData/Local/Android/Sdk/platform-tools/adb.exe'));p.add_argument('--serial',required=True);p.add_argument('--out',type=Path,default=Path('artifacts/phone-learning'));a=p.parse_args();logs=a.out/'logs';logs.mkdir(parents=True,exist_ok=True)
 result=subprocess.run([a.adb,'-s',a.serial,'exec-out','run-as','com.idr.navigator','tar','cf','-','files/drives'],capture_output=True,check=True)
 with tarfile.open(fileobj=io.BytesIO(result.stdout)) as archive:
  for member in archive:
   name=Path(member.name).name
   if not member.isfile() or member.size>500*1024*1024 or not name.endswith(('.drlog','.quality.csv','.training.json')):continue
   try:
    if str(uuid.UUID(name[:36]))!=name[:36]:continue
   except ValueError:continue
   (logs/name).write_bytes(archive.extractfile(member).read())
 d,report=build_dataset(logs.glob('*.drlog'));np.savez_compressed(a.out/'phone-dataset.npz',**d)
 report['candidate']=train(d,Path('android/app/src/main/assets/models/speed.tflite'),a.out)
 (a.out/'phone-learning-report.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))
if __name__=='__main__':main()
