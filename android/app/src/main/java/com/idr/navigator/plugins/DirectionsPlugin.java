package com.idr.navigator.plugins;

import com.getcapacitor.*;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.idr.navigator.engine.OfflineDirections;
import com.idr.navigator.engine.EngineDiagnostics;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@CapacitorPlugin(name = "Directions")
public final class DirectionsPlugin extends Plugin {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean();

    @PluginMethod public void route(PluginCall call) {
        Double fromLat=call.getDouble("fromLat"), fromLon=call.getDouble("fromLon"), toLat=call.getDouble("toLat"), toLon=call.getDouble("toLon");
        if (fromLat==null || fromLon==null || toLat==null || toLon==null) { call.reject("Select a starting point and destination."); return; }
        try { OfflineDirections.validate(fromLat,fromLon); OfflineDirections.validate(toLat,toLon); }
        catch (IllegalArgumentException e) { call.reject(e.getMessage()); return; }
        if (!busy.compareAndSet(false,true)) { call.reject("A route is still being calculated. Please try again shortly."); return; }
        worker.execute(() -> {
            try (OfflineDirections router = new OfflineDirections(graphDirectory().getAbsolutePath())) {
                var result = router.route(fromLat,fromLon,toLat,toLon,call.getString("mode","car"));
                JSArray points = new JSArray();
                for (int i=0; i<result.points().size(); i++) {
                    JSArray point = new JSArray();
                    point.put(result.points().getLon(i)); point.put(result.points().getLat(i)); points.put(point);
                }
                JSObject response = new JSObject();
                response.put("coordinates", points); response.put("distance",result.distance()); response.put("duration",result.time()/1000.0);
                JSArray steps=new JSArray();
                for(var step:result.steps()) {
                    JSObject s=new JSObject();s.put("text",step.text());s.put("sign",step.sign());s.put("lat",step.lat());s.put("lon",step.lon());s.put("distance",step.distance());s.put("duration",step.duration());steps.put(s);
                }
                response.put("steps",steps);
                call.resolve(response);
            } catch (Exception | LinkageError | OutOfMemoryError error) {
                EngineDiagnostics.failure(getContext(),"Calculating destination route",error);
                call.reject(error instanceof IllegalArgumentException ? error.getMessage() : "Offline directions could not be loaded. Please try again.");
            } finally { busy.set(false); }
        });
    }

    private File graphDirectory() throws IOException {
        File trackingGraph = new File(getContext().getFilesDir(),"maps/mp-vehicle-v1");
        if (new File(trackingGraph,"bundled.ready").isFile()) return trackingGraph;
        // Manual endpoints work even when GPS permission was denied. Use a private cache
        // so extraction never races the unchanged tracking service's first-launch copy.
        File directory = new File(getContext().getFilesDir(),"directions/mp-vehicle-v1");
        File ready = new File(directory,"bundled.ready");
        if (!ready.isFile()) {
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot prepare offline directions");
            String[] names = getContext().getAssets().list("maps/mp-vehicle-v1");
            if (names==null || names.length==0) throw new IOException("Offline road data missing");
            for (String name:names) {
                File staged = new File(directory,name+".tmp");
                try (InputStream in=getContext().getAssets().open("maps/mp-vehicle-v1/"+name); OutputStream out=new FileOutputStream(staged)) {
                    byte[] buffer=new byte[65536]; int n; while ((n=in.read(buffer))!=-1) out.write(buffer,0,n);
                }
                Files.move(staged.toPath(),new File(directory,name).toPath(),StandardCopyOption.REPLACE_EXISTING);
            }
            if (!ready.createNewFile()) throw new IOException("Cannot finalize offline road data");
        }
        return directory;
    }
    @Override protected void handleOnDestroy() { worker.shutdown(); }
}
