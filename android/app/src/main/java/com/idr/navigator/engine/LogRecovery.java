package com.idr.navigator.engine;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
/** Recovers complete records from an interrupted local log without rewriting its bytes. */
public final class LogRecovery {
 public record Result(String phone,double durationSeconds,double distanceMeters){}
 public static Result read(File file,String session)throws IOException {
  try(RandomAccessFile input=new RandomAccessFile(file,"r")) {
   if(input.length()<200)throw new IOException("Incomplete header; raw file retained");
   byte[] h=new byte[200];input.readFully(h);
   if(!new String(h,0,5,StandardCharsets.US_ASCII).equals("DRLOG")||h[5]!=1||!field(h,136).equals(session))throw new IOException("Recording identity mismatch");
   long first=0,last=0;double distance=0;byte[] row=new byte[70];
   while(input.length()-input.getFilePointer()>=70){input.readFully(row);ByteBuffer b=ByteBuffer.wrap(row).order(ByteOrder.LITTLE_ENDIAN);long t=b.getLong(0);if(t<=last)continue;if(first==0)first=t;double dt=last==0?0:(t-last)*1e-9;float speed=b.getFloat(56),accuracy=b.getFloat(60);if(dt<=.25&&speed>=0&&speed<=80&&accuracy>0&&accuracy<=25&&(row[69]&1)!=0)distance+=speed*dt;last=t;}
   return new Result(field(h,8),Math.max(0,(last-first)*1e-9),distance);
  }
 }
 /** Preserve the raw interrupted file; upload only complete records in a separate copy. */
 public static File uploadable(File raw,String session)throws IOException {
  read(raw,session);
  long valid=200+(raw.length()-200)/70*70;
  if(valid==raw.length())return raw;
  File repaired=new File(raw.getParentFile(),session+"-recovered.drlog");
  if(repaired.exists()){if(repaired.length()==valid){read(repaired,session);return repaired;}throw new IOException("Existing recovery copy needs review");}
  try(InputStream in=new FileInputStream(raw);OutputStream out=java.nio.file.Files.newOutputStream(repaired.toPath(),java.nio.file.StandardOpenOption.CREATE_NEW)){
   byte[] buffer=new byte[65536];long remaining=valid;while(remaining>0){int n=in.read(buffer,0,(int)Math.min(remaining,buffer.length));if(n<0)throw new EOFException();out.write(buffer,0,n);remaining-=n;}
  }
  return repaired;
 }
 private static String field(byte[] bytes,int offset){int end=offset;while(end<offset+64&&bytes[end]!=0)end++;return new String(bytes,offset,end-offset,StandardCharsets.UTF_8);}
}
