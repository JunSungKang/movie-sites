package xyz.gg_jskang.moviesites.playlist.internal;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class WholeVideoSelectionTests {
    @Test void fullVideosUseConservativeMillisecondsAndNeverOverrunTheBudget(){
        SelectionPolicy policy=new WholeVideoSelection();
        assertEquals(60_000,policy.acceptedMillis(59.9994,60_000));assertEquals(-1,policy.acceptedMillis(60.0001,60_000));
        assertEquals(-1,policy.acceptedMillis(Double.NaN,60_000));assertEquals(-1,policy.acceptedMillis(null,60_000));assertEquals(-1,policy.acceptedMillis(0.0,60_000));
    }
}
