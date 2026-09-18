package com.example.hybrid_ai_app.testing

import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Freshly generated identifiers, so no literal is shared between tests.
 *
 * A hard-coded `"test@example.com"` or `"tok-1"` repeated across suites is an invisible coupling:
 * the moment two tests disagree about what that value means, the failure points at the assertion
 * rather than at the shared constant. The rule is that whoever asserts on a value gets it from the
 * fixture that produced it — never from a constant both sides happen to reference.
 *
 * Values are unique per call, not per run, so a test that stores two users cannot accidentally
 * collide them.
 */
object TestIds {

    private val counter = AtomicLong(0)

    /** A unique, obviously-fake address. `.test` is reserved by RFC 2606 and cannot resolve. */
    fun uniqueEmail(): String = "user-${UUID.randomUUID()}@example.test"

    /** A unique opaque bearer token. Not a real JWT — nothing here parses it. */
    fun uniqueToken(): String = "jwt-${UUID.randomUUID()}"

    /** A unique Play purchase token. */
    fun uniquePurchaseToken(): String = "purchase-${UUID.randomUUID()}"

    /**
     * A unique 24-character hex string, the shape Mongo `ObjectId` serialises to. The backend
     * sends these for `_id` and `userId`, so DTO tests need something of the right width.
     */
    fun uniqueObjectId(): String {
        val n = counter.incrementAndGet()
        return "%024x".format(n xor (UUID.randomUUID().mostSignificantBits and 0xFFFFFFFFFFFFL))
    }

    /** A unique human-ish name, for cases where the value only has to differ. */
    fun uniqueName(): String = "User ${counter.incrementAndGet()}"
}
