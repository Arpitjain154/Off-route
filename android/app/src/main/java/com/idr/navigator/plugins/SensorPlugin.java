package com.idr.navigator.plugins;

import com.getcapacitor.*;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.idr.navigator.engine.SensorCaptureService;
import java.io.*;

@CapacitorPlugin(name = "SensorPlugin")
public final class SensorPlugin extends Plugin {

    @PluginMethod
    public void shareDiagnostics(PluginCall call) {
        getBridge().execute(()->{
            try {
                String saved=getContext().getSharedPreferences("logs",0).getString("last","");
                File drive=new File(saved).getCanonicalFile(),dir=new File(getContext().getFilesDir(),"drives").getCanonicalFile();
                boolean hasDrive=drive.toPath().startsWith(dir.toPath())&&drive.isFile();
                File exports=new File(getContext().getCacheDir(),"exports");exports.mkdirs();
                File zip=new File(exports,hasDrive?drive.getName().replace(".drlog","-diagnostics.zip"):"idr-engine-diagnostics.zip");
                try(java.util.zip.ZipOutputStream out=new java.util.zip.ZipOutputStream(new FileOutputStream(zip))) {
                    out.putNextEntry(new java.util.zip.ZipEntry("engine-diagnostics.txt"));
                    com.idr.navigator.engine.EngineDiagnostics.export(getContext(),out);
                    out.closeEntry();
                    File trace=new File(dir,drive.getName().replace(".drlog",".fusion.csv"));
                    for(File file:hasDrive?new File[]{drive,trace,new File(dir,drive.getName().replace(".drlog",".quality.csv")),new File(dir,drive.getName().replace(".drlog",".training.json")),new File(dir,drive.getName().replace(".drlog",".summary.json"))}:new File[0])if(file.isFile()) {
                        out.putNextEntry(new java.util.zip.ZipEntry(file.getName()));
                        try(InputStream in=new FileInputStream(file)) { byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n); }
                        out.closeEntry();
                    }
                }
                android.net.Uri uri=androidx.core.content.FileProvider.getUriForFile(getContext(),getContext().getPackageName()+".fileprovider",zip);
                android.content.Intent send=new android.content.Intent(android.content.Intent.ACTION_SEND).setType("application/zip")
                    .putExtra(android.content.Intent.EXTRA_STREAM,uri).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                getActivity().runOnUiThread(()->{
                    try { getActivity().startActivity(android.content.Intent.createChooser(send,"Export IDR diagnostics"));call.resolve(); }
                    catch(RuntimeException e) { call.reject("No app is available to share this report",e); }
                });
            }catch(Exception e){call.reject(e.getMessage());}
        });
    }

    @PluginMethod
    public void startLogging(PluginCall call) {
        SensorCaptureService s = SensorCaptureService.instance;
        if (s == null) {
            call.reject("Start the engine first");
            return;
        }
        String id = call.getString("sessionId", ""),
            mount = call.getString("mount", "dashboard");
        int rate = call.getInt("sampleRate", 100);
        try {
            java.util.UUID.fromString(id);
            if (
                !java.util.Arrays.asList("dashboard", "windshield", "console").contains(mount) ||
                (rate != 100 && rate != 200)
            ) throw new Exception();
        } catch (Exception e) {
            call.reject("Invalid recording settings");
            return;
        }
        s.handler.post(() -> {
            try {
                s.startLog(id, mount, rate);
                call.resolve();
            } catch (Exception e) {
                call.reject(e.getMessage());
            }
        });
    }

    @PluginMethod
    public void stopLogging(PluginCall call) {
        SensorCaptureService s = SensorCaptureService.instance;
        if (s == null) {
            call.reject("Engine is not running");
            return;
        }
        s.handler.post(() -> {
            try {
                call.resolve(s.stopLog());
            } catch (Exception e) {
                call.reject(e.getMessage());
            }
        });
    }

    @PluginMethod
    public void recoverLog(PluginCall call) {
        Runnable read=()->{
            try {
                SensorCaptureService engine=SensorCaptureService.instance;
                if(engine!=null&&engine.isLogging())throw new IOException("Finish the active recording before recovery");
                String id=call.getString("sessionId","");
                if(!java.util.UUID.fromString(id).toString().equals(id))throw new IOException("Invalid session identity");
                File file=new File(new File(getContext().getFilesDir(),"drives"),id+".drlog");
                File summary=new File(file.getParentFile(),id+".summary.json");
                JSObject r;
                if(summary.isFile())r=new JSObject(new String(java.nio.file.Files.readAllBytes(summary.toPath()),java.nio.charset.StandardCharsets.UTF_8));
                else {
                    var log=com.idr.navigator.engine.LogRecovery.read(file,id);
                    File upload=com.idr.navigator.engine.LogRecovery.uploadable(file,id);
                    r=new JSObject();r.put("path",upload.toURI().toString());r.put("sizeBytes",upload.length());
                    r.put("durationSeconds",log.durationSeconds());r.put("distanceMeters",log.distanceMeters());r.put("phoneModel",log.phone());r.put("recovered",true);
                }
                getContext().getSharedPreferences("logs",0).edit().putString("last",file.getAbsolutePath()).apply();
                call.resolve(r);
            }catch(Exception e){call.reject("Recording recovery: "+e.getMessage());}
        };
        SensorCaptureService engine=SensorCaptureService.instance;
        if(engine!=null)engine.handler.post(read);else getBridge().execute(read);
    }

    @PluginMethod
    public void getLogFile(PluginCall call) {
        try {
            String value = call.getString(
                "path",
                getContext().getSharedPreferences("logs", 0).getString("last", "")
            );
            File file = value.startsWith("file:")
                ? new File(java.net.URI.create(value))
                : new File(value);
            File directory = new File(getContext().getFilesDir(), "drives").getCanonicalFile();
            if (
                !file.getCanonicalFile().toPath().startsWith(directory.toPath()) || !file.isFile()
            ) throw new IOException("Recording not found");
            JSObject result = new JSObject();
            result.put("path", file.toURI().toString());
            result.put("sizeBytes", file.length());
            call.resolve(result);
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }
}
