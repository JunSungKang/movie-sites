package xyz.gg_jskang.moviesites.playlist.internal;
final class WholeVideoSelection implements SelectionPolicy {
    @Override public long acceptedMillis(Double seconds,long remaining){
        if(seconds==null||!Double.isFinite(seconds)||seconds<=0)return -1;
        double millis=Math.ceil(seconds*1000);
        return millis<=remaining?(long)millis:-1;
    }
}
