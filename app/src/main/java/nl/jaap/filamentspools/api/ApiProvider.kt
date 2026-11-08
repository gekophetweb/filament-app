package nl.jaap.filamentspools.api

import kotlinx.coroutines.runBlocking
import nl.jaap.filamentspools.BuildConfig
import nl.jaap.filamentspools.data.session.SessionStore
import nl.jaap.filamentspools.data.session.SignOutReason
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Builds [FilamentApi] clients for the current server; rebuilt when the server URL changes. */
@Singleton
class ApiProvider @Inject constructor(private val session: SessionStore) {

    private val baseClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(Interceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("Accept", "application/json").build())
            })
            .build()
    }

    private val authClient: OkHttpClient by lazy {
        baseClient.newBuilder()
            .addInterceptor(Interceptor { chain ->
                val token = session.current.value?.token
                val request = chain.request().newBuilder()
                    .apply { if (token != null) header("Authorization", "Bearer $token") }
                    .build()
                val response = chain.proceed(request)
                if (response.code == 401 && token != null) {
                    runBlocking { session.signOut(SignOutReason.TokenInvalid) }
                }
                response
            })
            .apply { if (BuildConfig.DEBUG) addInterceptor(logging()) }
            .build()
    }

    @Volatile private var cached: Pair<String, FilamentApi>? = null

    /** Client for the signed-in session. Throws [ApiError.Unauthorized] when not paired. */
    fun api(): FilamentApi {
        val baseUrl = session.current.value?.baseUrl ?: throw ApiError.Unauthorized
        cached?.let { (url, api) -> if (url == baseUrl) return api }
        return build(baseUrl, authClient).also { cached = baseUrl to it }
    }

    /** Client with a token that is not stored yet (checking a pasted token). A 401 does not sign out. */
    fun withToken(baseUrl: String, token: String): FilamentApi = build(
        baseUrl,
        baseClient.newBuilder()
            .addInterceptor(Interceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer $token").build())
            })
            .build(),
    )

    /** Client without a token, for pairing. */
    fun unauthenticated(baseUrl: String): FilamentApi = build(baseUrl, baseClient)

    private fun build(baseUrl: String, client: OkHttpClient): FilamentApi =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(FilamentApi::class.java)

    private fun logging() = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
        redactHeader("Authorization")
    }
}
