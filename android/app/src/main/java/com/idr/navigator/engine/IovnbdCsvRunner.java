package com.idr.navigator.engine;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;

/** Raw IO-VNBD transport demo, not a frame-calibrated drift evaluation. SI engine boundary. */
public final class IovnbdCsvRunner {
    private record Row(double seconds,double[] values) {}
    public static void main(String[] args) throws Exception {
        if(args.length<4)throw new IllegalArgumentException("input.csv output.csv --gps-speed-unit mps|kmh [--resample-hz 200] [--outage-start seconds --outage-duration seconds]");
        Map<String,String> options=new HashMap<>();
        for(int i=2;i<args.length;i+=2){if(i+1==args.length)throw new IllegalArgumentException("Missing option value");options.put(args[i],args[i+1]);}
        String unit=options.get("--gps-speed-unit");
        if(!"mps".equals(unit)&&!"kmh".equals(unit))throw new IllegalArgumentException("Explicit GNSS speed unit required");
        double scale="kmh".equals(unit)?1/3.6:1;
        double hz=Double.parseDouble(options.getOrDefault("--resample-hz","0"));
        if(hz!=0&&hz!=200)throw new IllegalArgumentException("Supported resampling is 200 Hz");
        double start=Double.parseDouble(options.getOrDefault("--outage-start","Infinity"));
        double duration=Double.parseDouble(options.getOrDefault("--outage-duration","0"));
        if(duration<0)throw new IllegalArgumentException("Negative outage duration");
        List<Row> rows=new ArrayList<>();
        try(BufferedReader in=Files.newBufferedReader(Path.of(args[0]),StandardCharsets.ISO_8859_1)) {
            in.readLine();String line;double initial=Double.NaN;
            while((line=in.readLine())!=null) {
                String[] fields=line.split(",",-1);if(fields.length<24)throw new IOException("Expected 24-column phone CSV");
                double[] values=new double[24];Arrays.fill(values,Double.NaN);
                for(int i=0;i<24;i++)if(i!=8)try{values[i]=Double.parseDouble(fields[i].trim());}catch(NumberFormatException ignored){}
                double time=values[7]/1000.;if(!Double.isFinite(time))continue;
                if(!Double.isFinite(initial))initial=time;
                double seconds=time-initial;
                if(!rows.isEmpty()&&seconds<=rows.get(rows.size()-1).seconds())continue;
                rows.add(new Row(seconds,values));
            }
        }
        if(rows.isEmpty())throw new IOException("No increasing finite timestamps");
        EdgeNavigator edge=new EdgeNavigator();boolean outage=false;double[] lastFix=null;
        double[] up={rows.get(0).values()[9],rows.get(0).values()[10],rows.get(0).values()[11]};
        double norm=Math.sqrt(up[0]*up[0]+up[1]*up[1]+up[2]*up[2]);
        if(!Double.isFinite(norm)||norm<1)throw new IOException("Invalid initial accelerometer gravity estimate");
        for(int i=0;i<3;i++)up[i]*=9.80665/norm;
        Path output=Path.of(args[1]);if(output.toAbsolutePath().getParent()!=null)Files.createDirectories(output.toAbsolutePath().getParent());
        int index=0,lastSource=-1,count=0;double end=rows.get(rows.size()-1).seconds();
        try(PrintWriter out=new PrintWriter(Files.newBufferedWriter(output))) {
            out.println("seconds,lat,lon,speed,heading,uncertainty,available,estimated,gnss_used,speed_source,source_seconds");
            for(int tick=0;;tick++) {
                double seconds=hz==0?(tick<rows.size()?rows.get(tick).seconds():Double.POSITIVE_INFINITY):tick/hz;
                if(seconds>end+1e-8)break;
                while(index+1<rows.size()&&rows.get(index+1).seconds()<=seconds+1e-9)index++;
                Row row=rows.get(index);double[] r=row.values();long t=1_000_000_000L+Math.round(seconds*1e9);
                boolean hidden=seconds>=start&&seconds<start+duration;
                if(hidden&&!outage)edge.gnssUnavailable();outage=hidden;
                // Constant recorded GRAVITY columns are deliberately ignored. This transport
                // fixture uses initial acceleration for tilt, not an accuracy-grade mount calibration.
                edge.gravity(t,up[0],up[1],up[2]);
                edge.compass(t,r[21],15);
                edge.imu(new ImuSample(t,r[9],r[10],r[11],r[15],r[16],r[17]),Double.isFinite(r[15]+r[16]+r[17]));
                boolean used=false;
                double[] tuple={r[0],r[1],r[3],r[4],r[5]};
                if(!hidden&&index!=lastSource&&!Arrays.equals(tuple,lastFix)) {
                    used=edge.gnss(new NavigationFusion.Fix(t,r[0],r[1],r[2],r[4],r[3]*scale,.5,r[5],10),t);
                    if(used)lastFix=tuple;
                }
                lastSource=index;EdgeNavigator.Pose p=edge.position(t);
                out.printf(Locale.ROOT,"%.9f,%.12f,%.12f,%.9f,%.9f,%.9f,%b,%b,%b,%s,%.9f%n",seconds,p.latitude(),p.longitude(),p.speed(),p.heading(),p.uncertainty(),p.available(),p.estimated(),used,p.speedSource().replace(',',';'),row.seconds());count++;
            }
        }
        System.out.println("Wrote "+count+" samples; rate="+(hz==0?"native":hz)+"; initial-acceleration tilt / raw gyro transport only, not accuracy validation");
    }
}
