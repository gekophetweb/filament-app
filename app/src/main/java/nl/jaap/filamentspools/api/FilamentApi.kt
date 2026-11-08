package nl.jaap.filamentspools.api

import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** The Filament Spools REST API (`<server>/api/v1/`). Paths are relative to the base URL. */
interface FilamentApi {

    @POST("pair")
    suspend fun pair(@Body body: PairRequest): Envelope<PairedDeviceDto>

    @GET("me")
    suspend fun me(): Envelope<MeDto>

    @GET("catalog")
    suspend fun catalog(
        @Query("per_page") perPage: Int,
        @Query("page") page: Int,
        @Query("updated_since") updatedSince: String? = null,
    ): Page<ProductDto>

    @GET("spools")
    suspend fun spools(
        @Query("per_page") perPage: Int,
        @Query("page") page: Int,
        @Query("updated_since") updatedSince: String? = null,
    ): Page<SpoolDto>

    @GET("spools/{uuid}")
    suspend fun spool(@Path("uuid") uuid: String): Envelope<SpoolDto>

    @POST("spools")
    suspend fun createSpool(@Body body: CreateSpoolRequest): Envelope<List<SpoolDto>>

    @PATCH("spools/{uuid}")
    suspend fun patchSpool(@Path("uuid") uuid: String, @Body body: JsonObject): Envelope<SpoolDto>

    @HTTP(method = "DELETE", path = "spools/{uuid}", hasBody = true)
    suspend fun deleteSpool(@Path("uuid") uuid: String, @Body body: NoteRequest): Response<Unit>

    @POST("spools/{uuid}/open")
    suspend fun openSpool(@Path("uuid") uuid: String, @Body body: NoteRequest): Envelope<SpoolDto>

    @POST("spools/{uuid}/used-up")
    suspend fun useUpSpool(@Path("uuid") uuid: String, @Body body: NoteRequest): Envelope<SpoolDto>

    @GET("spools/{uuid}/events")
    suspend fun events(@Path("uuid") uuid: String, @Query("page") page: Int): Page<SpoolEventDto>
}
