package com.idr.navigator.engine;

import java.io.*;
import java.nio.file.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RawTrainingWriterTest {
    private final String id="12345678-1234-1234-1234-123456789abc";
    @Test public void retainsEveryRawEventAndDoubleCoordinates() throws Exception {
        File dir=Files.createTempDirectory("raw-training").toFile();
        try(RawTrainingWriter w=new RawTrainingWriter(dir,id)) {
            w.sensor(12345678901234567L,4,0,new float[]{1,2,3});
            w.sensor(12345678901234568L,4,3,new float[]{4,5,6});
            w.sensor(12345678901234569L,11,1,new float[]{0,0,0,1,.125f});
            w.gnss(12345678901234567L,12345678901234599L,1700000000000L,23.1234567890123,77.1234567890123,500,4,6,12,.3,45,3,true,false);
        }
        java.util.List<String> rows=Files.readAllLines(new File(dir,id+".sensors.csv").toPath());
        assertEquals(4,rows.size());assertTrue(rows.get(1).startsWith("12345678901234567,4,0,3,"));
        assertTrue(rows.get(3).endsWith("0.0,0.0,0.0,1.0,0.125"));
        String[] fix=Files.readAllLines(new File(dir,id+".gnss.csv").toPath()).get(1).split(",");
        assertEquals(23.1234567890123,Double.parseDouble(fix[3]),0);assertEquals("1",fix[12]);assertEquals("0",fix[13]);
        assertThrows(IOException.class,()->new RawTrainingWriter(dir,id));
    }
    @Test public void closedWriterRejectsFurtherEvents() throws Exception {
        RawTrainingWriter w=new RawTrainingWriter(Files.createTempDirectory("raw-closed").toFile(),id);
        w.close();w.close();assertThrows(IOException.class,()->w.sensor(1,1,3,new float[]{1,2,3}));
    }
    @Test public void rawLogCloseStillClosesAfterFlushFailure() throws Exception {
        DrLogWriter w=new DrLogWriter(Files.createTempFile("drlog-close",".drlog").toFile(),100,"test","test",id);
        java.lang.reflect.Field field=DrLogWriter.class.getDeclaredField("stream");field.setAccessible(true);
        ((BufferedOutputStream)field.get(w)).close();
        final boolean[] closed={false};
        BufferedOutputStream failing=new BufferedOutputStream(new ByteArrayOutputStream()) {
            @Override public void flush() throws IOException {throw new IOException("disk full");}
            @Override public void close(){closed[0]=true;}
        };
        field.set(w,failing);assertThrows(IOException.class,w::close);assertTrue(closed[0]);
    }
}
