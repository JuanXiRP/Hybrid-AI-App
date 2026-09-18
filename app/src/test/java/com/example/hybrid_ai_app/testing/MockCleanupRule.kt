package com.example.hybrid_ai_app.testing

import io.mockk.clearAllMocks
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Clears MockK state between tests, in one place, so no suite has to remember to.
 *
 * **`clearAllMocks` vs `unmockkAll` vs MockK's reset flags — the decision, and why.**
 *
 * `clearAllMocks()` wipes *both* recorded calls and stubbed answers from every mock created so
 * far, and it also clears `mockkObject`/`mockkStatic` recordings without removing the static
 * mocking itself. That means:
 *
 *  - A stub declared in `@Before` is gone by the time the *next* test runs, so `@Before` must
 *    re-declare every `every {}` / `coEvery {}` it needs. Every suite here does exactly that, so
 *    the cost is zero and the benefit is that no test can inherit a stub from its predecessor.
 *  - It is the opposite trade-off from the backend's `tests/setupAfterEach.js`, where
 *    `clearAllMocks` was chosen *because* it preserves the implementations installed by
 *    `jest.mock` factories and `resetAllMocks` would have destroyed them. Jest's "clear" keeps
 *    implementations; MockK's `clearAllMocks` does not keep answers. Same function name, opposite
 *    semantics — hence this note.
 *
 * We deliberately do **not** call `unmockkAll()` here: it would tear down `mockkStatic` /
 * `mockkObject` registrations that a suite installs once in a `@BeforeClass`-style setup. Suites
 * that install static mocks per-test undo them in their own `@After`.
 *
 * Runs *after* each test rather than before, so a failing test's recorded calls are still intact
 * while JUnit builds the failure message.
 */
class MockCleanupRule : TestWatcher() {
    override fun finished(description: Description) {
        clearAllMocks()
    }
}
