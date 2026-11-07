package nl.jaap.filamentspools.api

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerUrlTest {

    @Test
    fun addsSchemeAndApiPath() =
        assertEquals("http://192.168.1.5:8000/api/v1/", ServerUrl.normalize("192.168.1.5:8000"))

    @Test
    fun trimsSpacesAndTrailingSlash() =
        assertEquals("http://10.0.2.2:8000/api/v1/", ServerUrl.normalize(" http://10.0.2.2:8000/api/v1/ "))

    @Test
    fun keepsHttps() =
        assertEquals("https://spools.example.com/api/v1/", ServerUrl.normalize("https://spools.example.com"))

    @Test
    fun keepsExistingApiPath() =
        assertEquals("http://host/api/v1/", ServerUrl.normalize("http://host/api/v1"))

    @Test(expected = IllegalArgumentException::class)
    fun blankThrows() {
        ServerUrl.normalize("  ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun garbageThrows() {
        ServerUrl.normalize("http://")
    }
}
