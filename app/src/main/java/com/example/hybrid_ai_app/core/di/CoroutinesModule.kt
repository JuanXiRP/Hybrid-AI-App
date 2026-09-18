package com.example.hybrid_ai_app.core.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Marks the application-lifetime [CoroutineScope] used for work that must outlive the caller.
 *
 * The one current use is the backend sync in `WorkoutPlanRepositoryImpl.completeWorkout`: the
 * local Room write is what the UI waits on, while pushing the session to MongoDB is
 * fire-and-forget and must not be cancelled when the screen goes away.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {

    /**
     * Provided rather than constructed inline at the call site so tests can substitute a
     * `TestScope`. A repository that builds its own `CoroutineScope(Dispatchers.IO)` launches work
     * onto a dispatcher the test scheduler knows nothing about, which makes any assertion on that
     * work a race: `coVerify` either needs an arbitrary timeout or passes only by luck.
     *
     * [SupervisorJob] so one failed sync cannot cancel the scope and silently disable every
     * later sync for the rest of the process.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
