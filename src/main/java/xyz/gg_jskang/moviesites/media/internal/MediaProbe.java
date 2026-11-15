package xyz.gg_jskang.moviesites.media.internal;
record MediaProbe(Double duration,Integer width,Integer height,String codec,String audio,String pixel,String transfer,int videoIndex,int audioIndex,int videoCount,int audioCount) {
    boolean copyCompatible(){return codec.equals("h264")&&pixel.equals("yuv420p")&&(audioIndex<0||audio.equals("aac"));}
}
