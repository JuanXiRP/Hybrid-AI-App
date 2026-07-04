package com.example.hybrid_ai_app.core.di

import com.example.hybrid_ai_app.BuildConfig
import com.example.hybrid_ai_app.coach.data.CoachApi
import com.example.hybrid_ai_app.core.data.PreferencesManager
import com.example.hybrid_ai_app.core.data.remote.TokenAuthenticator
import com.example.hybrid_ai_app.core.data.remote.UserApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.CertificatePinner
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://hybrid-ai-backend-7h7k.onrender.com/"
    private const val BACKEND_HOST = "hybrid-ai-backend-7h7k.onrender.com"

    @Provides
    @Singleton
    fun provideOkHttpClient(preferencesManager: PreferencesManager): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            // Only log bodies in debug builds; never print credential headers
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("Authorization")
            redactHeader("Cookie")
        }

        val authInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            // Safe synchronous read for Interceptors
            val token = preferencesManager.getTokenSync()

            val requestBuilder = originalRequest.newBuilder()

            if (!token.isNullOrEmpty()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }

            chain.proceed(requestBuilder.build())
        }

        // Pin the Google Trust Services CA public keys (intermediate + root) that sign the
        // backend's certificate. Pinning the CA rather than the leaf survives cert rotation.
        val certificatePinner = CertificatePinner.Builder()
            .add(
                BACKEND_HOST,
                "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4=", // GTS WE1 (intermediate)
                "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c="  // GTS Root R4 (backup)
            )
            .build()

        return OkHttpClient.Builder()
            .connectTimeout(90, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .certificatePinner(certificatePinner)
            .authenticator(TokenAuthenticator(preferencesManager))
            .addInterceptor(loggingInterceptor)
            .addInterceptor(authInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        val networkJson = Json { ignoreUnknownKeys = true }

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(networkJson.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideUserApi(retrofit: Retrofit): UserApi = retrofit.create(UserApi::class.java)

    @Provides
    @Singleton
    fun provideCoachApi(retrofit: Retrofit): CoachApi = retrofit.create(CoachApi::class.java)
}