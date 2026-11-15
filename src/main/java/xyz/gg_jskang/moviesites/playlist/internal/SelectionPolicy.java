package xyz.gg_jskang.moviesites.playlist.internal;
/** A pure selection Strategy; negative cost means that a candidate must be skipped. */
interface SelectionPolicy {long acceptedMillis(Double seconds,long remaining);}
