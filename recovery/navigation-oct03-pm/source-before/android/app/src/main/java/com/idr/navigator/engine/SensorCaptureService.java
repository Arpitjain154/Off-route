package com.idr.navigator.engine;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.hardware.*;
import android.location.*;
import android.os.*;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.JSObject;
import com.idr.navigator.MainActivity;
import java.io.*;
import java.util.concurrent.*;

/** Sensor and fusion state confined to one HandlerThread; HMM on a separate bounded worker. */
public final class SensorCaptureService
    extends Service
    implements SensorEventListener, LocationListener
{

    public static volatile SensorCaptureService instance;
    public static volatile String startupError;
    private volatile boolean startupReady, stopping;
    private static final java.util.concurrent.atomic.AtomicInteger generations = new java.util.concurrent.atomic.AtomicInteger();
    private final int generation = generations.incrementAndGet();
    private int locationRegistrations;
    private String lastTransition = "";
    private HandlerThread thread;
    public Handler handler;
    private SensorManager sensors;
    private LocationManager locations;
    private PowerManager.WakeLock wake;
    private ExecutorService mapWorker;
    private volatile boolean mapBusy;
    private NavigationFusion navigation;
    private boolean researchAvailable=true;
    private final LiveNavigation live=new LiveNavigation();
    private final float[] phoneGravity={0,0,9.80665f};
    private float declination;
    private Eskf filter;
    private GpsAdvisor gps;
    private PhysicsAdvisor physics;
    private AiSpeedAdvisor ai;
    private MapMatchAdvisor map;
    private final FeatureBuffer features = new FeatureBuffer();
    public TfliteModels models;
    private final float[] acc = { 0, 0, 9.80665f },
        gyro = new float[3],
        mag = new float[3];
    private long lastImu, lastAdvisors, lastInference, lastMap, lastFix, logStarted;
    private double logDistance;
    private DrLogWriter logger;
    private FusionTraceWriter trace;
    private TrainingQualityWriter trainingQuality;
    private String captureFailure;
    private long lastCalibrationFix;
    public boolean isLogging(){return logger!=null;}
    private Location lastLocation;
    private volatile JSObject snapshot = new JSObject();
    private String health = "BLACKOUT",
        modelStatus = "Loading models";
    private long lastGyro,modelInferences;
    private final AiSpeedReading aiSpeedReading=new AiSpeedReading();
    private float[] previousHealth = new float[5];
    private long previousHealthTime;

    public void onCreate() {
        super.onCreate();
        synchronized(ModelInstaller.LOCK) { instance = this; }
        startupError = null;
        EngineDiagnostics.stage(this, "Starting foreground service");
        try {
        thread = new HandlerThread(
            "IDR-Sensors",
            android.os.Process.THREAD_PRIORITY_MORE_FAVORABLE
        );
        thread.start();
        handler = new Handler(thread.getLooper());
        mapWorker = Executors.newSingleThreadExecutor();
        sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
        locations = (LocationManager) getSystemService(LOCATION_SERVICE);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(
            new NotificationChannel(
                "idr-tracking",
                "Live navigation",
                NotificationManager.IMPORTANCE_LOW
            )
        );
        PendingIntent open = PendingIntent.getActivity(
            this,
            0,
            new Intent(this, MainActivity.class),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        startForeground(
            41,
            new NotificationCompat.Builder(this, "idr-tracking")
                .setSmallIcon(com.idr.navigator.R.drawable.ic_tracking)
                .setContentTitle("Offroute is tracking")
                .setContentText("GPS and motion tracking are active.")
                .setOngoing(true)
                .setContentIntent(open)
                .build()
        );
        wake = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "IDR:SensorCapture"
        );
        wake.acquire();
        handler.post(this::setup);
        } catch (RuntimeException | LinkageError e) { fail("Starting foreground service", e); }
    }

    private void setup() {
        if (stopping) return;
        try {
            EngineDiagnostics.stage(this, "Initializing fusion");
            navigation = new NavigationFusion();
            filter = navigation.filter; gps = navigation.gps; physics = navigation.physics;
            ai = new AiSpeedAdvisor(filter); map = new MapMatchAdvisor(filter);
            setupComponents();
            if (!stopping) {
                publish(SystemClock.elapsedRealtimeNanos());
                startupReady = true;
                EngineDiagnostics.stage(this, "Engine running");
            }
        } catch (RuntimeException | LinkageError e) { fail("Initializing tracking", e); }
    }

    public boolean isReady() { return startupReady && !stopping; }

    private void fail(String stage, Throwable error) {
        if (stopping) return;
        startupReady = false;
        startupError = stage + ": " + error.getClass().getSimpleName() +
            (error.getMessage() == null ? "" : " — " + error.getMessage());
        EngineDiagnostics.failure(this, stage, error);
        stopping = true;
        new Handler(Looper.getMainLooper()).post(this::stopSelf);
    }

    private void setupComponents() {
        EngineDiagnostics.stage(this, "Loading bundled models");
        try {
            models = new TfliteModels(this);
            modelStatus = "IO-VNBD models loaded · device validation pending";
        } catch (Exception | LinkageError e) {
            modelStatus = "Models unavailable: " + e.getMessage();
            EngineDiagnostics.failure(this, "Loading models", e);
        }
        EngineDiagnostics.stage(this, "Registering motion sensors");
        if (sensors == null || sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) == null)
            throw new IllegalStateException("This phone has no available accelerometer");
        for (int type : new int[] {
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_MAGNETIC_FIELD,
            Sensor.TYPE_GRAVITY,
            Sensor.TYPE_ROTATION_VECTOR,
        }) {
            Sensor s = sensors.getDefaultSensor(type);
            if (s != null) sensors.registerListener(
                this,
                s,
                SensorManager.SENSOR_DELAY_FASTEST,
                0,
                handler
            );
        }
        EngineDiagnostics.stage(this, "Registering GPS");
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            locations.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                500,
                0,
                this,
                thread.getLooper()
            );
            locationRegistrations++;
            // Raw measurements are optional. A vendor callback failure must not disable GPS fixes.
            try {
                androidx.core.location.LocationManagerCompat.registerGnssMeasurementsCallback(locations, command -> handler.post(command), gnss);
            } catch (RuntimeException | LinkageError e) {
                EngineDiagnostics.failure(this, "Raw GNSS measurements unavailable", e);
            }
        } else throw new SecurityException("Allow precise location in Android app permissions and try again");
        mapWorker.execute(() -> {
            try {
                if (stopping) return;
                File dir = new File(getFilesDir(), "maps");
                dir.mkdirs();
                File osm = new File(dir, "madhya-pradesh.osm.pbf");
                if (!osm.exists()) try (
                    InputStream in = getAssets().open("maps/madhya-pradesh.osm.pbf");
                    OutputStream out = new FileOutputStream(osm)
                ) {
                    byte[] copyBuffer = new byte[65536];
                    int copyCount;
                    while ((copyCount = in.read(copyBuffer)) != -1)
                        out.write(copyBuffer, 0, copyCount);
                }
                File graph = new File(dir, "mp-vehicle-v1");
                File ready = new File(graph, "bundled.ready");
                if (!ready.exists()) {
                    graph.mkdirs();
                    // Ship the JVM-verified graph so first launch does not require an OSM XML import.
                    // The bundled OSM extract remains available as the reproducible source.
                    String[] entries = getAssets().list("maps/mp-vehicle-v1");
                    if (entries != null && entries.length > 0) {
                        for (String name : entries) {
                            File staged = new File(graph, name + ".tmp");
                            try (
                                InputStream in = getAssets().open("maps/mp-vehicle-v1/" + name);
                                OutputStream out = new FileOutputStream(staged)
                            ) {
                                byte[] buffer = new byte[65536];
                                int count;
                                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                            }
                            java.nio.file.Files.move(
                                staged.toPath(),
                                new File(graph, name).toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING
                            );
                        }
                        ready.createNewFile();
                    }
                }
                map.load(osm.getAbsolutePath(), graph.getAbsolutePath());
            } catch (Exception | LinkageError | OutOfMemoryError e) {
                map.status = "Map unavailable: " + e.getClass().getSimpleName();
                map.close();
                EngineDiagnostics.failure(this, "Loading offline road graph", e);
            }
        });
        handler.post(ticker);
    }

    private final GnssMeasurementsEvent.Callback gnss = new GnssMeasurementsEvent.Callback() {
        public void onGnssMeasurementsReceived(GnssMeasurementsEvent e) {
            int count = 0;
            double sum = 0;
            for (GnssMeasurement m : e.getMeasurements())
                if (m.getCn0DbHz() > 0) {
                    count++;
                    sum += m.getCn0DbHz();
                }
            gps.satellites(count, count == 0 ? 0 : sum / count);
        }
    };

    public void onSensorChanged(SensorEvent e) {
        if (stopping) return;
        try { processSensor(e); }
        catch (RuntimeException | LinkageError error) { fail("Processing motion sensors", error); }
    }

    private void processSensor(SensorEvent e) {
        if (e.sensor.getType() == Sensor.TYPE_GRAVITY) {
            System.arraycopy(e.values,0,phoneGravity,0,3);
            navigation.gravity(e.timestamp,e.values[0],e.values[1],e.values[2]);
            live.gravity(e.timestamp,e.values[0],e.values[1],e.values[2]);return;
        }
        if(e.sensor.getType()==Sensor.TYPE_ROTATION_VECTOR) {
            if(e.accuracy<SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM)return;
            float[] rotation=new float[9];SensorManager.getRotationMatrixFromVector(rotation,e.values);
            // Match the horizontal phone reference used by tilt alignment, including
            // upright portrait mounting. Rotation-vector orientation is magnetic ENU.
            double norm=Math.sqrt(phoneGravity[0]*phoneGravity[0]+phoneGravity[1]*phoneGravity[1]+phoneGravity[2]*phoneGravity[2]);
            double[] axis=Math.abs(phoneGravity[1]/Math.max(1,norm))>.85?new double[]{0,0,-1}:new double[]{0,1,0};
            double east=rotation[0]*axis[0]+rotation[1]*axis[1]+rotation[2]*axis[2];
            double north=rotation[3]*axis[0]+rotation[4]*axis[1]+rotation[5]*axis[2];
            double error=e.values.length>=5&&e.values[4]>=0?Math.toDegrees(e.values[4]):e.accuracy==SensorManager.SENSOR_STATUS_ACCURACY_HIGH?15:30;
            if(Math.hypot(east,north)>.25)live.compass(e.timestamp,Math.toDegrees(Math.atan2(east,north))+declination,error);
            return;
        }
        if (e.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            System.arraycopy(e.values, 0, gyro, 0, 3);
            lastGyro=e.timestamp;
            return;
        }
        if (e.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(e.values, 0, mag, 0, 3);
            return;
        }
        if (e.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        System.arraycopy(e.values, 0, acc, 0, 3);
        acceptImu(new ImuSample(e.timestamp,acc[0],acc[1],acc[2],gyro[0],gyro[1],gyro[2]),lastGyro>0&&Math.abs(e.timestamp-lastGyro)<100_000_000L);
    }

    /** Sensor-provider boundary; called on the engine HandlerThread with SI units. */
    public void acceptImu(ImuSample raw,boolean gyroFresh) {
        if(stopping)return;
        long t = raw.t();
        double dt = lastImu == 0 ? .01 : (t - lastImu) * 1e-9;
        if (dt < .004) return;
        lastImu = t;
        live.imu(raw,gyroFresh);
        if(researchAvailable)try {navigation.imu(raw,gyroFresh);}
        catch(RuntimeException | LinkageError error){researchAvailable=false;EngineDiagnostics.failure(this,"Research filter disabled; live navigation continues",error);}
        if(gyroFresh)features.add(raw);
        if (logger != null) {
            if(trace!=null)try { trace.write(t,navigation,live); } catch(IOException failure) { modelStatus="Trace write failed: "+failure.getMessage(); }
            if (live.trackingAvailable(t)) logDistance += displayedSpeed(t) * Math.min(.1, dt);
            boolean valid = lastLocation != null && lastFix>0 && t>=lastFix && t-lastFix<2_000_000_000L;
            if(trainingQuality!=null)try {trainingQuality.write(t,valid?lastFix:0,valid&&lastLocation.hasSpeedAccuracy()?lastLocation.getSpeedAccuracyMetersPerSecond():Double.NaN,gyroFresh);}
            catch(IOException e){captureFailure="Training quality write failed";try{java.nio.file.Files.write(new File(logger.file.getParentFile(),logger.file.getName().replace(".drlog",".capture-error")).toPath(),captureFailure.getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(IOException ignored){}try{trainingQuality.close();}catch(IOException ignored){}trainingQuality=null;EngineDiagnostics.failure(this,"Training quality recording stopped",e);}
            logger.write(
                t,
                new float[]{(float)raw.ax(),(float)raw.ay(),(float)raw.az()},
                new float[]{(float)raw.gx(),(float)raw.gy(),(float)raw.gz()},
                mag,
                valid ? (float) lastLocation.getLatitude() : Float.NaN,
                valid ? (float) lastLocation.getLongitude() : Float.NaN,
                valid ? (float) lastLocation.getAltitude() : Float.NaN,
                valid && lastLocation.hasSpeed() ? lastLocation.getSpeed() : Float.NaN,
                valid ? lastLocation.getAccuracy() : Float.NaN,
                gps.satellites,
                (float) gps.cn0,
                valid ? 3 : 0
            );
        }
    }

    public void onLocationChanged(Location l) {
        if (stopping) return;
        try {
        long now=SystemClock.elapsedRealtimeNanos();
        NavigationFusion.Fix fix=new NavigationFusion.Fix(l.getElapsedRealtimeNanos(),l.getLatitude(),l.getLongitude(),
            l.hasAltitude()?l.getAltitude():Double.NaN,l.hasAccuracy()?l.getAccuracy():Double.NaN,
            l.hasSpeed()?l.getSpeed():Double.NaN,l.hasSpeedAccuracy()?l.getSpeedAccuracyMetersPerSecond():Double.NaN,
            l.hasBearing()?l.getBearing():Double.NaN,l.hasBearingAccuracy()?l.getBearingAccuracyDegrees():Double.NaN);
        if(live.fix(fix,now)) {
            lastLocation=new Location(l);lastFix=fix.time();
            declination=new GeomagneticField((float)l.getLatitude(),(float)l.getLongitude(),(float)(l.hasAltitude()?l.getAltitude():0),System.currentTimeMillis()).getDeclination();
            if(researchAvailable)try {navigation.fix(fix,now);}
            catch(RuntimeException | LinkageError error){researchAvailable=false;EngineDiagnostics.failure(this,"Research GPS filter disabled; live navigation continues",error);}
        }
        } catch (RuntimeException | LinkageError e) { fail("Processing GPS fix", e); }
    }

    private double displayedSpeed(long now) { return live.speed(now); }

    @Override public void onProviderDisabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) { navigation.providerDisabled();live.providerDisabled();lastFix=0; health="BLACKOUT";publish(SystemClock.elapsedRealtimeNanos()); }
    }
    @Override public void onProviderEnabled(String provider) { if(LocationManager.GPS_PROVIDER.equals(provider))live.providerEnabled(); }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}

    private final Runnable ticker = new Runnable() {
        public void run() {
            if (stopping) return;
            long now = SystemClock.elapsedRealtimeNanos();
            try {
                if (researchAvailable && filter.initialized) {
                    for (FilterAdvisor advisor : new FilterAdvisor[] { map, ai }) {
                        if (advisor==map && (physics.isStopped(now) ||
                            (now-lastFix<2_000_000_000L && gps.accuracy()<=15))) continue;
                        Measurement m = advisor.provide(now);
                        filter.update(m, advisor.trustScore());
                    }
                }
                if (now - lastInference >= 1_000_000_000L) {
                    lastInference = now;
                    double age = (now - lastFix) * 1e-9;
                    health =
                        lastFix == 0 || age > 2
                            ? "BLACKOUT"
                            : live.accuracy() > 20 || (gps.satellites > 0 && gps.satellites < 5)
                              ? "DEGRADING"
                              : "HEALTHY";
                    float[] base = {
                        (float) (age > 2 ? 0 : gps.satellites),
                        (float) (age > 2 ? 0 : gps.cn0),
                        (float) gps.accuracy(),
                        lastLocation != null && lastLocation.hasSpeed() && age < 2 ? 1 : 0,
                        (float) Math.min(age, 30),
                    };
                    float[] healthFeatures = new float[10];
                    double delta = previousHealthTime == 0 ? 1 : (now - previousHealthTime) * 1e-9;
                    for (int i = 0; i < 5; i++) {
                        healthFeatures[i] = base[i];
                        healthFeatures[i + 5] = (float) ((base[i] - previousHealth[i]) /
                            Math.max(.1, delta));
                    }
                    previousHealth = base;
                    previousHealthTime = now;
                    if (models != null) try {
                        float[] prediction = models.health(healthFeatures);
                        if (features.ready(now)&&live.imuFresh(now)) {
                            float[] motion = models.motion(features);
                            double speedPrediction=models.speed(features);
                            aiSpeedReading.update(now,models.placeholder("speed")?Double.NaN:speedPrediction);
                            live.inertial.models(now,models.enabled("speed")?speedPrediction:Double.NaN,
                                models.enabled("motion")?motion:null,models.enabled("gps-health")?prediction:null,
                                lastLocation!=null&&lastLocation.hasSpeed()?(double)lastLocation.getSpeed():null,
                                live.healthy(now)&&live.accuracy()<=15&&now-lastFix<=1_000_000_000L&&live.imuFresh(now)&&lastFix>lastCalibrationFix&&lastLocation!=null&&lastLocation.hasSpeedAccuracy()&&lastLocation.getSpeedAccuracyMetersPerSecond()>=0&&lastLocation.getSpeedAccuracyMetersPerSecond()<=1);
                            lastCalibrationFix=Math.max(lastCalibrationFix,lastFix);
                            modelInferences++;
                        } else aiSpeedReading.clear();
                        modelStatus = models.placeholder("speed")
                            ? "Placeholder ML · AI speed disabled"
                            : !models.enabled("speed") ? "AI inference only · awaiting phone validation"
                            : live.inertial.aiWeight(now)>0 ? "IO-VNBD inference active · calibrated speed corrections"
                            : "IO-VNBD inference active · speed calibration " + live.inertial.calibrationSamples()+"/15; physical drift validation pending";
                    } catch(RuntimeException | LinkageError error) {
                        modelStatus="Model inference unavailable · inertial tracking continues";
                        aiSpeedReading.clear();
                        live.inertial.models(now,Double.NaN,null,null,null,false);
                        EngineDiagnostics.failure(SensorCaptureService.this,"Model inference",error);
                        models.close();models=null;
                    }
                }
                if (live.trackingAvailable(now) && now - lastMap > 2_000_000_000L && !mapBusy) {
                    lastMap = now;
                    mapBusy = true;
                    double lat = live.latitude(),
                        lon = live.longitude();
                    mapWorker.execute(() -> {
                        try {
                            map.observe(now, lat, lon);
                        } catch (RuntimeException | LinkageError | OutOfMemoryError error) {
                            // Optional road matching must never take the sensor service/process down.
                            map.disable(error);
                            EngineDiagnostics.failure(SensorCaptureService.this, "Matching GPS position to roads", error);
                        } finally {
                            mapBusy = false;
                        }
                    });
                }
                var match=map.latest();
                if(match!=null)live.inertial.road(match.time(),match.sourceLat(),match.sourceLon(),match.lat(),match.lon(),match.heading(),match.trust());
                publish(now);
            } catch (Exception | LinkageError e) {
                fail("Updating tracking engine", e);
            }
            if (!stopping) handler.postDelayed(this, 100);
        }
    };

    private void publish(long now) {
        live.tick(now);
        if (!Double.isFinite(live.latitude()) || !Double.isFinite(live.longitude()) ||
            !Double.isFinite(live.heading()) || !Double.isFinite(displayedSpeed(now)))
            throw new IllegalStateException("Navigation produced a non-finite observation");
        JSObject s = new JSObject();
        s.put("engineGeneration", generation);
        s.put("locationRegistrations", locationRegistrations);
        s.put("lat",live.latitude());
        s.put("lon",live.longitude());
        s.put("heading",live.heading());
        s.put("positionFresh",live.fresh(now));
        s.put("estimated",live.estimated(now));
        s.put("trackingAvailable",live.trackingAvailable(now));
        s.put("estimateAge",live.fixTime()==0?-1:(now-live.fixTime())*1e-9);
        s.put("fixTime",live.fixTime()/1e6);
        s.put("speedAvailable",live.speedAvailable(now));
        s.put("headingSource",live.headingSource(now));
        s.put("speed", displayedSpeed(now));
        s.put("stationary",live.stopped(now));
        s.put("speedSource",live.speedSource(now));
        s.put("fusionStatus",live.status(now));
        s.put("headingReady",live.headingReady(now));
        s.put("imuFresh",live.imuFresh(now));
        s.put("gpsAccuracy",lastLocation==null?0:lastLocation.getAccuracy());
        s.put("gpsAge",lastFix==0?-1:(now-lastFix)*1e-9);
        s.put("accuracy",live.uncertainty(now));
        s.put(
            "confidence",
            live.trackingAvailable(now) ? Math.max(0, Math.min(1, Math.exp(-live.uncertainty(now) / 50))) : 0
        );
        s.put("health", health);
        s.put("ready",live.ready());
        s.put("logging", logger != null);
        s.put("source", "device");
        s.put("gpsTrust", now - lastFix > 2_000_000_000L ? 0 : gps.trustScore());
        s.put("physicsTrust", physics.trustScore());
        s.put("mapTrust", map.trustScore());
        s.put("aiTrust", live.inertial.aiWeight(now));
        s.put("aiSpeedRmse",live.inertial.modelRmse());
        boolean aiAvailable=aiSpeedReading.available(now)&&live.imuFresh(now)&&features.ready(now);
        s.put("aiSpeedAvailable",aiAvailable);
        s.put("aiSpeedEstimate",aiAvailable?aiSpeedReading.speed(now):0);
        s.put("aiSpeedValidated",models!=null&&models.enabled("speed"));
        s.put("modelInferences",modelInferences);
        s.put("distance", logDistance);
        s.put("elapsed", logger == null ? 0 : (now - logStarted) * 1e-9);
        s.put("bytes", logger == null ? 0 : logger.size());
        s.put("satellites", now - lastFix > 3_000_000_000L ? 0 : gps.satellites);
        s.put("cn0", now - lastFix > 3_000_000_000L ? 0 : gps.cn0);
        s.put("mapStatus", map.status);
        s.put("modelStatus", modelStatus);
        String transition = live.status(now);
        if (!transition.equals(lastTransition)) {
            lastTransition = transition;
            EngineDiagnostics.stage(this, transition);
            android.util.Log.i("IDR-Engine", "generation=" + generation + " locationRegistrations=" + locationRegistrations + " " + transition);
        }
        snapshot = s;
    }

    public JSObject position() {
        return snapshot;
    }

    public void startLog(String session, String mount, int rate) throws IOException {
        if (logger != null) throw new IOException("A drive is already recording");
        captureFailure=null;
        if (!live.ready()) throw new IOException("Wait for the initial GPS fix");
        File dir = new File(getFilesDir(), "drives");
        dir.mkdirs();
        if(new File(dir,session+".drlog").exists())throw new IOException("Recording already exists");
        logger = new DrLogWriter(
            new File(dir, session + ".drlog"),
            rate,
            Build.MANUFACTURER + " " + Build.MODEL,
            mount,
            session
        );
        logStarted = SystemClock.elapsedRealtimeNanos();
        try { trainingQuality=new TrainingQualityWriter(dir,session,LaptopTrialProvenance.activeHash(this));trace=new FusionTraceWriter(new File(dir,session+".fusion.csv")); }
        catch(IOException e) { logger.close();logger=null;if(trainingQuality!=null){trainingQuality.close();trainingQuality=null;}throw e; }
        logDistance = 0;
        com.idr.navigator.cloud.CloudScheduler.began(this,session);
    }

    public JSObject stopLog() throws IOException {
        if (logger == null) throw new IOException("No active recording");
        DrLogWriter done = logger;
        logger = null;
        done.close();
        if(trace!=null){try{trace.close();}finally{trace=null;}}
        if(trainingQuality!=null){try{trainingQuality.close();}finally{trainingQuality=null;}}
        getSharedPreferences("logs", 0)
            .edit()
            .putString("last", done.file.getAbsolutePath())
            .apply();
        JSObject r = new JSObject();
        r.put("path", done.file.toURI().toString());
        r.put("sizeBytes", done.size());
        r.put("durationSeconds", (SystemClock.elapsedRealtimeNanos() - logStarted) * 1e-9);
        r.put("distanceMeters", logDistance);
        r.put("phoneModel", Build.MANUFACTURER + " " + Build.MODEL);
        if(done.error!=null||captureFailure!=null) {
            String failure=done.error!=null?"Raw recording write failed":captureFailure;
            try {java.nio.file.Files.write(new File(done.file.getParentFile(),done.file.getName().replace(".drlog",".capture-error")).toPath(),failure.getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(IOException ignored){}
            r.put("cloudWarning",failure+"; raw recording retained");return r;
        }
        try {java.nio.file.Files.write(new File(done.file.getParentFile(),done.file.getName().replace(".drlog",".summary.json")).toPath(),r.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        catch(IOException e){EngineDiagnostics.failure(this,"Saving recording summary",e);}
        if (done.error != null) throw new IOException(done.error);
        com.idr.navigator.cloud.CloudScheduler.completed(this,done.file.getName().replace(".drlog",""));
        return r;
    }

    public int onStartCommand(Intent i, int flags, int id) {
        return START_NOT_STICKY;
    }

    public IBinder onBind(Intent i) {
        return null;
    }

    public void onAccuracyChanged(Sensor s, int accuracy) {}

    public void onDestroy() {
        stopping = true;
        startupReady = false;
        if (sensors != null) sensors.unregisterListener(this);
        if (locations != null) try {
            locations.removeUpdates(this);
            androidx.core.location.LocationManagerCompat.unregisterGnssMeasurementsCallback(locations, gnss);
        } catch (RuntimeException e) { EngineDiagnostics.failure(this, "Unregistering GPS", e); }
        if (handler != null) {
        handler.removeCallbacks(ticker);
        handler.post(() -> {
            try {
                if (logger != null) stopLog();
            } catch (IOException ignored) {}
            try { if (models != null) models.close(); }
            finally { synchronized(ModelInstaller.LOCK) { if(instance==this)instance=null; } thread.quitSafely(); }
        });
        } else { synchronized(ModelInstaller.LOCK) { if(instance==this)instance=null; } }
        if (mapWorker != null) {
        mapWorker.execute(() -> { if (map != null) map.close(); });
        mapWorker.shutdown();
        }
        if (wake != null && wake.isHeld()) wake.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}
