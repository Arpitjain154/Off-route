package com.idr.navigator.engine;

/** Conservative experimental heading gate; locks one edge until GNSS recovery. */
final class OutageRoadConstraint {
    private long last;
    private int edge=-1, count;
    void reset(){last=0;edge=-1;count=0;}
    boolean accept(long time,int nextEdge,double trust,double offset,double angle) {
        if(time<=last)return false;
        boolean continuous=last>0&&time-last<=3_000_000_000L;
        last=time;
        if(nextEdge<0||!Double.isFinite(trust+offset+angle)||trust<.6||trust>1||offset>5||offset<0||Math.abs(angle)>15){count=0;return false;}
        if(edge>=0&&edge!=nextEdge)return false;
        edge=nextEdge;count=continuous?count+1:1;
        return count>=2;
    }
}
