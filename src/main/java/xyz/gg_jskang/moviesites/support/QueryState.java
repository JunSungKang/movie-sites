package xyz.gg_jskang.moviesites.support;
import java.util.Locale;
/** Explicit finite transitions for asynchronous snapshots, not a class-per-state hierarchy. */
public enum QueryState {
    PREPARING,READY,FAILED,CANCELLED;
    public String wire(){return name().toLowerCase(Locale.ROOT);}
    public QueryState transition(QueryState next){
        if(this==next)return this;
        if((this==PREPARING&&(next==READY||next==FAILED||next==CANCELLED))||((this==READY||this==FAILED)&&next==CANCELLED))return next;
        throw new IllegalStateException("Invalid query transition: "+this+" -> "+next);
    }
}
