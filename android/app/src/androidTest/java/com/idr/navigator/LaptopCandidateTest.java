package com.idr.navigator;

import static org.junit.Assert.*;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.security.MessageDigest;
import org.json.JSONObject;
import org.junit.Test;
import org.tensorflow.lite.Interpreter;

/** Isolated TFLite test only: never installs candidate weights into navigation. */
public class LaptopCandidateTest {
    @Test public void candidateMatchesDesktopInference() throws Exception {
        var args = InstrumentationRegistry.getArguments();
        org.junit.Assume.assumeTrue("Run through laptop agent", args.containsKey("candidate_sha256"));
        File dir = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir(), "laptop-candidate");
        byte[] binary = Files.readAllBytes(new File(dir,"candidate.tflite").toPath());
        StringBuilder hash = new StringBuilder();
        for(byte b:MessageDigest.getInstance("SHA-256").digest(binary)) hash.append(String.format(java.util.Locale.ROOT,"%02x",b));
        assertEquals(args.getString("candidate_sha256"),hash.toString());
        ByteBuffer model = ByteBuffer.allocateDirect(binary.length).order(ByteOrder.nativeOrder());
        model.put(binary); model.rewind();
        ByteBuffer fixtures = ByteBuffer.wrap(Files.readAllBytes(new File(dir,"phone-parity.bin").toPath())).order(ByteOrder.LITTLE_ENDIAN);
        int count=fixtures.getInt(); assertTrue(count>0 && count<=64); double maximum=0;
        try(Interpreter runner=new Interpreter(model, new Interpreter.Options().setNumThreads(1))) {
            assertArrayEquals(new int[]{1,300,6},runner.getInputTensor(0).shape());
            assertArrayEquals(new int[]{1,1},runner.getOutputTensor(0).shape());
            assertEquals(org.tensorflow.lite.DataType.FLOAT32,runner.getInputTensor(0).dataType());
            assertEquals(org.tensorflow.lite.DataType.FLOAT32,runner.getOutputTensor(0).dataType());
            for(int i=0;i<count;i++) {
                float[][][] input=new float[1][300][6];
                for(int j=0;j<300;j++)for(int k=0;k<6;k++)input[0][j][k]=fixtures.getFloat();
                float expected=fixtures.getFloat(); float[][] output=new float[1][1]; runner.run(input,output);
                assertTrue(Float.isFinite(output[0][0]));
                maximum=Math.max(maximum,Math.abs(output[0][0]-expected));
                assertEquals("Android/desktop inference differs",expected,output[0][0],.001);
            }
        }
        assertFalse(fixtures.hasRemaining());
        JSONObject result=new JSONObject().put("passed",true).put("nonce",args.getString("nonce"))
            .put("candidate_sha256",hash.toString()).put("windows",count).put("max_absolute_error_mps",maximum);
        Files.write(new File(dir,"result.json").toPath(),result.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
