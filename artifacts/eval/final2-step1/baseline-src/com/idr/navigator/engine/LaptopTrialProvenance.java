package com.idr.navigator.engine;

import android.content.Context;
import com.idr.navigator.cloud.TripPackage;
import java.io.*;
import org.json.JSONObject;

/** Metadata only. Ordinary builds retain their existing behaviour and model gates. */
public final class LaptopTrialProvenance {
    public static String activeHash(Context context) throws IOException {
        if (!context.getPackageName().equals("com.idr.navigator.candidate")) return "";
        try (InputStream in = context.getAssets().open("laptop-trial.json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer=new byte[4096]; int count;
            while((count=in.read(buffer))!=-1)bytes.write(buffer,0,count);
            String expected = new JSONObject(bytes.toString("UTF-8")).getString("candidate_sha256");
            File model = new File(context.getFilesDir(),"models/speed.tflite");
            if(!expected.matches("[a-f0-9]{64}") || !expected.equals(TripPackage.hash(model)) ||
               !context.getSharedPreferences("models",0).getBoolean("speed.validated",false))
                throw new IOException("Candidate trial model changed or is disabled; reinstall the trial from the laptop");
            return expected;
        } catch(Exception error) { throw new IOException("Cannot bind this recording to the candidate trial",error); }
    }
}
