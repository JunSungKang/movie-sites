package xyz.gg_jskang.moviesites.support;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class QueryStateTests {
    @Test void completedAndCancelledQueriesCannotPublishAgain(){
        assertEquals(QueryState.READY,QueryState.PREPARING.transition(QueryState.READY));
        assertEquals(QueryState.CANCELLED,QueryState.READY.transition(QueryState.CANCELLED));
        assertThrows(IllegalStateException.class,()->QueryState.READY.transition(QueryState.PREPARING));
        assertThrows(IllegalStateException.class,()->QueryState.CANCELLED.transition(QueryState.READY));
    }
}
