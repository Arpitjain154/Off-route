package com.idr.navigator.engine;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;

/** Lossless callback sidecars. Device axes and original Android timestamps are retained. */
public final class RawTrainingWriter implements AutoCloseable {
    public static final String SENSOR_HEADER="t_ns,sensor_type,accuracy,value_count,v0,v1,v2,v3,v4";
    public static final String GNSS_HEADER="fix_t_ns,arrival_t_ns,utc_ms,latitude_deg,longitude_deg,altitude_m,horizontal_accuracy_m,vertical_accuracy_m,speed_mps,speed_accuracy_mps,bearing_deg,bearing_accuracy_deg,mock,accepted";
    private final BufferedWriter sensors,gnss;
    private boolean closed;
    public RawTrainingWriter(File directory,String session) throws IOException {
        if(!UUID.fromString(session).toString().equals(session))throw new IOException("Invalid session identity");
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Cannot create recording directory");
        File sensorFile=new File(directory,session+".sensors.csv"),gnssFile=new File(directory,session+".gnss.csv");
        if(sensorFile.exists()||gnssFile.exists())throw new IOException("Raw session already exists");
        BufferedWriter first=Files.newBufferedWriter(sensorFile.toPath(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        BufferedWriter second=null;
        try {
            second=Files.newBufferedWriter(gnssFile.toPath(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            first.write(SENSOR_HEADER+"\n");second.write(GNSS_HEADER+"\n");
        } catch(IOException e) {try{first.close();}catch(IOException x){e.addSuppressed(x);}if(second!=null)try{second.close();}catch(IOException x){e.addSuppressed(x);}throw e;}
        sensors=first;gnss=second;
    }
    public synchronized void sensor(long time,int type,int accuracy,float[] values) throws IOException {
        if(closed)throw new IOException("Raw recording is closed");
        if(values.length>5)throw new IOException("Unsupported sensor value count");
        sensors.write(time+","+type+","+accuracy+","+values.length);
        for(int i=0;i<5;i++){sensors.write(',');if(i<values.length)sensors.write(Float.toString(values[i]));}
        sensors.write('\n');
    }
    public synchronized void gnss(long fix,long arrival,long utc,double lat,double lon,double alt,double hAcc,double vAcc,double speed,double speedAcc,double bearing,double bearingAcc,boolean mock,boolean accepted) throws IOException {
        if(closed)throw new IOException("Raw recording is closed");
        gnss.write(fix+","+arrival+","+utc);
        for(double v:new double[]{lat,lon,alt,hAcc,vAcc,speed,speedAcc,bearing,bearingAcc}){gnss.write(',');gnss.write(Double.toString(v));}
        gnss.write(mock?",1":",0");gnss.write(accepted?",1\n":",0\n");
    }
    @Override public synchronized void close() throws IOException {
        if(closed)return;closed=true;IOException error=null;
        try{sensors.close();}catch(IOException e){error=e;}
        try{gnss.close();}catch(IOException e){if(error==null)error=e;else error.addSuppressed(e);}
        if(error!=null)throw error;
    }
}
