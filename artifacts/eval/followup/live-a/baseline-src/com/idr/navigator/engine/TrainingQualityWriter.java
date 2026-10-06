package com.idr.navigator.engine;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/** Local training provenance; DRLOG v1 remains backward compatible. */
public final class TrainingQualityWriter implements AutoCloseable {
    private final BufferedWriter writer;
    private boolean closed;
    public TrainingQualityWriter(File directory,String session) throws IOException {
        this(directory,session,"");
    }
    public TrainingQualityWriter(File directory,String session,String trialHash) throws IOException {
        this(directory,session,trialHash,false);
    }
    public TrainingQualityWriter(File directory,String session,String trialHash,boolean rawSidecars) throws IOException {
        if(!trialHash.isEmpty()&&!trialHash.matches("[a-f0-9]{64}"))throw new IOException("Invalid candidate trial hash");
        if(!UUID.fromString(session).toString().equals(session))throw new IOException("Invalid session identity");
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Cannot create recording directory");
        File quality=new File(directory,session+".quality.csv");
        File metadata=new File(directory,session+".training.json");
        // Never truncate a prior session, even if a caller mistakenly reuses its UUID.
        if(metadata.exists()||quality.exists())throw new IOException("Training session already exists");
        BufferedWriter opened=Files.newBufferedWriter(quality.toPath(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        try {
            opened.write("t_ns,fix_t_ns,speed_accuracy_mps,gyro_fresh\n");
            String trial=trialHash.isEmpty()?"":",\"candidateTrialSha256\":\""+trialHash+"\"";
            String raw=rawSidecars?",\"rawSidecarsSchemaVersion\":1,\"rawSidecars\":[\".sensors.csv\",\".gnss.csv\"],\"labelSource\":\"receiver_gnss\",\"units\":{\"monotonicTime\":\"ns\",\"utcTime\":\"ms_since_unix_epoch\",\"acceleration\":\"m/s^2\",\"angularVelocity\":\"rad/s\",\"magneticField\":\"microtesla\",\"coordinates\":\"degrees_float64\",\"distance\":\"m\",\"speed\":\"m/s\",\"bearing\":\"degrees\",\"rotationVector\":\"Android_xyzw_and_heading_accuracy_rad\"},\"sensorFrame\":\"Android_device_axes\"":"";
            Files.write(metadata.toPath(),("{\"schemaVersion\":1,\"source\":\"device\",\"sessionId\":\""+session+"\""+trial+raw+"}\n").getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE_NEW);
        } catch(IOException failure) {try{opened.close();}catch(IOException ignored){}throw failure;}
        writer=opened;
    }
    public synchronized void write(long time,long fixTime,double speedAccuracy,boolean gyroFresh) throws IOException {
        if(closed)throw new IOException("Training quality recording is closed");
        writer.write(Long.toString(time));writer.write(',');writer.write(Long.toString(fixTime));writer.write(',');
        writer.write(Double.toString(speedAccuracy));writer.write(',');writer.write(gyroFresh?"1\n":"0\n");
    }
    @Override public synchronized void close() throws IOException {
        if(closed)return;
        closed=true;writer.close();
    }
}
