package ke.afyaclaim.pos.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ke.afyaclaim.pos.BuildConfig
import ke.afyaclaim.pos.data.local.AfyaDatabase
import ke.afyaclaim.pos.data.prefs.SessionStore
import ke.afyaclaim.pos.data.prefs.SyncCursorStore
import ke.afyaclaim.pos.data.remote.AfyaApi
import ke.afyaclaim.pos.data.remote.AuthInterceptor
import ke.afyaclaim.pos.domain.sale.PseudonymHasher
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AfyaDatabase =
        Room.databaseBuilder(context, AfyaDatabase::class.java, AfyaDatabase.NAME).build()

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = true
        encodeDefaults = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun okHttp(authInterceptor: AuthInterceptor): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
                redactHeader("Authorization")
            },
        )
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun api(client: OkHttpClient, json: Json): AfyaApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(AfyaApi::class.java)

    @Provides
    @Singleton
    fun pseudonymHasher(): PseudonymHasher = PseudonymHasher(BuildConfig.PSEUDONYM_SALT)

    @Provides
    @Singleton
    fun workManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun cursorStore(store: SessionStore): SyncCursorStore
}
