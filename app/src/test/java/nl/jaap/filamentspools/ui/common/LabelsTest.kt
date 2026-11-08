package nl.jaap.filamentspools.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class LabelsTest {
    @Test
    fun statusLabels() {
        assertEquals("On spool", statusLabel("on_spool"))
        assertEquals("Boxed", statusLabel("boxed"))
        assertEquals("lent_out", statusLabel("lent_out"))
    }

    @Test
    fun eventLabels() {
        assertEquals("Put on spool", eventLabel("opened"))
        assertEquals("Weighed", eventLabel("weighed"))
        assertEquals("Used", eventLabel("used"))
        assertEquals("Moved", eventLabel("moved"))
    }
}
