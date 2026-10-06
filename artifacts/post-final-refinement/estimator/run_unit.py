"""Compile isolated estimator tests; root separately runs the complete Android suite."""
from pathlib import Path
import shutil,subprocess,os,sys
root=Path.cwd();out=root/'artifacts/post-final-refinement/estimator'
variant=sys.argv[1];dest=out/variant;src=dest/'src';classes=dest/'classes'
src.mkdir(parents=True,exist_ok=True);classes.mkdir(exist_ok=True)
jdk=Path('C:/Users/surya/.jdks/jbr-21.0.11/bin')
cache=Path.home()/'.gradle/caches/modules-2/files-2.1'
jars=[next((cache/'junit/junit/4.13.2').glob('**/junit-4.13.2.jar')),next((cache/'org.hamcrest/hamcrest-core/1.3').glob('**/hamcrest-core-1.3.jar'))]
cp=os.pathsep.join(map(str,jars))
eng=Path('android/app/src/main/java/com/idr/navigator/engine')
for name in ['InertialNavigation.java','ImuSample.java']:shutil.copy2(eng/name,src/name)
tests=['VehicleVibrationRegressionTest','VehicleConstraintRegressionTest']
for name in tests:shutil.copy2(Path('android/app/src/test/java/com/idr/navigator/engine')/(name+'.java'),src/(name+'.java'))
(src/'NavigationFusion.java').write_text('package com.idr.navigator.engine; public class NavigationFusion { public record Fix(long time,double lat,double lon,double alt,double accuracy,double speed,double speedError,double bearing,double bearingError) { public boolean speedValid(){return Double.isFinite(speed)&&speed>=0&&speed<=80&&Double.isFinite(speedError)&&speedError>=0&&speedError<=1;} public boolean courseValid(){return speedValid()&&speed>3&&Double.isFinite(bearing)&&Double.isFinite(bearingError)&&bearingError>=0&&bearingError<=20;} } }',encoding='utf8')
subprocess.run([str(jdk/'javac.exe'),'-encoding','UTF-8','-cp',cp,'-d',str(classes),*map(str,src.glob('*.java'))],check=True)
result=subprocess.run([str(jdk/'java.exe'),'-cp',str(classes)+os.pathsep+cp,'org.junit.runner.JUnitCore',*['com.idr.navigator.engine.'+t for t in tests]],capture_output=True,text=True)
(dest/'run.log').write_text(result.stdout+result.stderr,encoding='utf8');print(result.stdout);sys.exit(result.returncode)
