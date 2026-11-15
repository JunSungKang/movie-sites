package xyz.gg_jskang.moviesites.media.internal;
import java.util.Locale;
enum MediaJobState {
    QUEUED,ANALYZING,INSPECTED,PREPARING,READY,FAILED;
    String wire(){return name().toLowerCase(Locale.ROOT);}
    MediaJobState transition(MediaJobState next){
        if(this==next)return this;
        boolean allowed=switch(this){
            case QUEUED -> next==ANALYZING||next==FAILED;
            case ANALYZING -> next==INSPECTED||next==READY||next==FAILED;
            case INSPECTED -> next==PREPARING||next==READY||next==FAILED;
            case PREPARING -> next==INSPECTED||next==READY||next==FAILED;
            case READY -> next==INSPECTED||next==FAILED;
            case FAILED -> false;
        };
        if(!allowed)throw new IllegalStateException("Invalid media transition: "+this+" -> "+next);
        return next;
    }
    boolean evictable(){return this==READY||this==FAILED||this==INSPECTED;}
}
