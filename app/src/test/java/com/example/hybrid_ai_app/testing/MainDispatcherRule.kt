package com.example.hybrid_ai_app.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Installs a [TestDispatcher] as `Dispatchers.Main` for the duration of a test.
 *
 * Every ViewModel here launches into `viewModelScope`, which is bound to `Dispatchers.Main` — a
 * dispatcher that does not exist on a JVM test JVM. Without this rule, constructing any ViewModel
 * with an `init {}` block throws `IllegalStateException: Module with the Main dispatcher had
 * failed to initialize`.
 *
 * The default is [StandardTestDispatcher], **not** `UnconfinedTestDispatcher`. Unconfined runs
 * every coroutine eagerly and depth-first at the point it is launched, which hides ordering bugs:
 * a test passes because the work happened to complete inside the builder call rather than because
 * the production code actually awaited it. Standard queues the work and makes the test say when it
 * should run (`advanceUntilIdle()`, `runCurrent()`), so the assertion reflects real sequencing.
 *
 * Pass [dispatcher] explicitly only for a test that genuinely needs eager execution, and say why
 * at the call site.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
