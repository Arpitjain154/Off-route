package com.idr.navigator.engine;

/** Semantic version comparison for bundled upgrades and prevention of OTA rollback. */
public final class ModelVersion {
    private ModelVersion() {}
    public static int compare(String a,String b) {
        String[] x=a.split("\\+",2)[0].split("-",2),y=b.split("\\+",2)[0].split("-",2);
        String[] xc=x[0].split("\\."),yc=y[0].split("\\.");
        if(xc.length!=3||yc.length!=3)throw new IllegalArgumentException("Invalid model version");
        for(int i=0;i<3;i++){int c=new java.math.BigInteger(xc[i]).compareTo(new java.math.BigInteger(yc[i]));if(c!=0)return c;}
        if(x.length==1||y.length==1)return Integer.compare(y.length,x.length);
        String[] xp=x[1].split("\\."),yp=y[1].split("\\.");
        for(int i=0;i<Math.min(xp.length,yp.length);i++) {
            boolean xn=xp[i].matches("[0-9]+"),yn=yp[i].matches("[0-9]+");
            int c=xn&&yn?new java.math.BigInteger(xp[i]).compareTo(new java.math.BigInteger(yp[i])):
                xn!=yn?(xn?-1:1):xp[i].compareTo(yp[i]);if(c!=0)return c;
        }
        return Integer.compare(xp.length,yp.length);
    }
}
