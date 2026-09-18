package com.example.hybrid_ai_app.testing

/**
 * The bodies `hybrid-ai-backend` actually sends, one named helper per endpoint and per failure.
 *
 * No suite writes a response JSON literal inline, and no suite writes a route string inline —
 * both live here, once. When the backend changes a field name, exactly one file needs editing and
 * every test that depends on it fails loudly, which is the whole point of pinning a contract that
 * is hand-mirrored rather than generated.
 *
 * These strings are transcribed from the backend source, not invented. Two details are easy to get
 * wrong and are preserved deliberately:
 *
 *  - **The wire is camelCase almost everywhere.** Only `has_completed_onboarding`,
 *    `last_period_date`, `plan_context`, `is_premium`, `trial_ends_at`, `trial_days_left` and
 *    `resets_at` are snake_case, plus Mongo's `_id`. Everything else (`durationWeeks`,
 *    `weekNumber`, `dayName`, `workoutType`, `fitnessLevel`, `daysAvailable`, ...) is camelCase.
 *  - **Mongoose adds fields no DTO declares**: `__v` on every document and an `_id` on *every*
 *    array subdocument, including each week, each day and each exercise. They are included here
 *    precisely so the tests prove `ignoreUnknownKeys` copes with them.
 *
 * `sets`, `reps` and `rpe` are **strings** on the wire, not numbers — the Gemini `responseSchema`
 * declares them as STRING and the import path writes `"-"` when the source states no value.
 */
object BackendResponses {

    // ---------------------------------------------------------------------------------------
    // Routes — the single source of truth for paths, mirroring the backend's
    // tests/helpers/routes.js. Retrofit's own annotations omit the leading slash; these include
    // it because that is what MockWebServer records as the request path.
    // ---------------------------------------------------------------------------------------

    object Routes {
        const val LOGIN = "/api/auth/login"
        const val REGISTER = "/api/auth/register"
        const val GOOGLE = "/api/auth/google"
        const val USER_PROFILE = "/api/users/profile"
        const val GENERATE_PLAN = "/api/ai/generate-plan"
        const val IMPORT_PLAN = "/api/ai/import-plan"
        const val CHAT = "/api/ai/chat"
        const val WORKOUTS_STRENGTH = "/api/workouts/strength"
        const val WORKOUTS_RUN = "/api/workouts/run"
        const val PLANS_ACTIVE = "/api/plans/active"
        const val PLANS_HISTORY = "/api/plans/history"
        const val BILLING_VERIFY = "/api/billing/verify"
        const val BILLING_ENTITLEMENT = "/api/billing/entitlement"
    }

    // ---------------------------------------------------------------------------------------
    // Auth
    // ---------------------------------------------------------------------------------------

    /**
     * `POST /api/auth/login` and `/api/auth/google`.
     *
     * Note `has_completed_onboarding` is a **sibling of `data`**, not a member of it, and the user
     * object here is the trimmed three-key shape using `"id"` — not `"_id"`, which is what the
     * full user document uses. The backend derives the flag from whether a WorkoutPlan exists, not
     * from the user's own `hasCompletedOnboarding` field, so the two can legitimately disagree.
     */
    fun loginSuccess(
        token: String,
        hasCompletedOnboarding: Boolean = false,
        userId: String = TestIds.uniqueObjectId(),
        name: String = "Ada",
        email: String = TestIds.uniqueEmail(),
    ): String = buildString {
        append("{\"success\":true,\"token\":\"").append(token).append("\",")
        append("\"has_completed_onboarding\":").append(hasCompletedOnboarding).append(",")
        append("\"data\":{\"id\":\"").append(userId).append("\",")
        append("\"name\":\"").append(name).append("\",")
        append("\"email\":\"").append(email).append("\"}}")
    }

    /** `POST /api/auth/register` — 201, and no `has_completed_onboarding` key at all. */
    fun registerSuccess(
        token: String,
        userId: String = TestIds.uniqueObjectId(),
        name: String = "Ada",
        email: String = TestIds.uniqueEmail(),
    ): String = buildString {
        append("{\"success\":true,\"token\":\"").append(token).append("\",")
        append("\"data\":{\"id\":\"").append(userId).append("\",")
        append("\"name\":\"").append(name).append("\",")
        append("\"email\":\"").append(email).append("\"}}")
    }

    // ---------------------------------------------------------------------------------------
    // User profile — the full Mongoose document
    // ---------------------------------------------------------------------------------------

    /**
     * `GET /api/users/profile`. The full user document: `_id`, camelCase profile fields, the one
     * snake_case `last_period_date`, the always-present `subscription` sub-object, and `__v`.
     *
     * `UserDto` declares 13 of these keys; the rest (`trialEndsAt`, `subscription`,
     * `hasCompletedOnboarding`, `createdAt`, `updatedAt`, `__v`) are unknown to it by design.
     */
    fun userProfile(
        userId: String = TestIds.uniqueObjectId(),
        email: String = TestIds.uniqueEmail(),
        name: String = "Ada",
        lastPeriodDate: String? = null,
        isPremium: Boolean = false,
    ): String = buildString {
        append("{\"success\":true,\"data\":{")
        append("\"_id\":\"").append(userId).append("\",")
        append("\"name\":\"").append(name).append("\",")
        append("\"email\":\"").append(email).append("\",")
        append("\"age\":30,\"weight\":80,\"height\":180,\"sex\":\"male\",\"goal\":\"both\",")
        append("\"fitnessLevel\":\"intermediate\",\"daysAvailable\":4,\"planDuration\":8,")
        append("\"injuries\":[],")
        append("\"last_period_date\":")
        append(lastPeriodDate?.let { "\"$it\"" } ?: "null")
        append(",\"trialEndsAt\":null,")
        append("\"isPremium\":").append(isPremium).append(",")
        append("\"subscription\":{\"purchaseToken\":null,\"productId\":null,\"orderId\":null,")
        append("\"expiryTime\":null,\"state\":null,\"acknowledged\":false,")
        append("\"lastVerifiedAt\":null},")
        append("\"hasCompletedOnboarding\":true,")
        append("\"createdAt\":\"2026-09-01T10:00:00.000Z\",")
        append("\"updatedAt\":\"2026-09-18T10:00:00.000Z\",\"__v\":0}}")
    }

    // ---------------------------------------------------------------------------------------
    // Plans
    // ---------------------------------------------------------------------------------------

    /**
     * `POST /api/ai/generate-plan` (201) and `GET /api/plans/active` (200) — both return the full
     * WorkoutPlan document under `data`.
     *
     * Every nested array element carries its own `_id` because none of the Mongoose subschemas set
     * `_id: false`, and none of the client DTOs declare it. `sets`/`reps`/`rpe` are strings.
     */
    fun workoutPlan(
        planId: String = TestIds.uniqueObjectId(),
        userId: String = TestIds.uniqueObjectId(),
        durationWeeks: Int = 8,
        goal: String = "both",
        origin: String = "generated",
        source: String = "generated",
        active: Boolean = true,
    ): String = buildString {
        append("{\"success\":true,\"data\":{")
        append("\"_id\":\"").append(planId).append("\",")
        append("\"userId\":\"").append(userId).append("\",")
        append("\"startDate\":\"2026-09-18T10:00:00.000Z\",")
        append("\"durationWeeks\":").append(durationWeeks).append(",")
        append("\"goal\":\"").append(goal).append("\",")
        append("\"weeks\":[{\"weekNumber\":1,\"days\":[{")
        append("\"dayName\":\"Lower Body\",\"workoutType\":\"strength\",")
        append("\"source\":\"").append(source).append("\",")
        append("\"exercises\":[{\"name\":\"Back Squat\",\"sets\":\"4\",\"reps\":\"6\",")
        append("\"rpe\":\"8\",\"_id\":\"").append(TestIds.uniqueObjectId()).append("\"}],")
        append("\"_id\":\"").append(TestIds.uniqueObjectId()).append("\"}],")
        append("\"_id\":\"").append(TestIds.uniqueObjectId()).append("\"}],")
        append("\"active\":").append(active).append(",")
        append("\"origin\":\"").append(origin).append("\",")
        append("\"createdAt\":\"2026-09-18T10:00:00.000Z\",")
        append("\"updatedAt\":\"2026-09-18T10:00:00.000Z\",\"__v\":0}}")
    }

    // ---------------------------------------------------------------------------------------
    // Coach chat
    // ---------------------------------------------------------------------------------------

    /** `POST /api/ai/chat` — only `reply` and `timestamp`; chat history never reaches the client. */
    fun chatReply(
        reply: String = "Focus on RPE 8 for your squats today.",
        timestamp: String = "2026-09-18T10:00:00.000Z",
    ): String = buildString {
        append("{\"success\":true,\"data\":{\"reply\":\"").append(reply).append("\",")
        append("\"timestamp\":\"").append(timestamp).append("\"}}")
    }

    // ---------------------------------------------------------------------------------------
    // Billing / entitlement
    // ---------------------------------------------------------------------------------------

    /**
     * `GET /api/billing/entitlement` and `POST /api/billing/verify`.
     *
     * This block is the one place with a real snake_case translation layer on the backend
     * (`billingController.toWire`). `status` is exactly `"premium" | "trial" | "expired"`, and
     * both limits are `null` for premium users — unlimited, not a large number.
     */
    fun entitlement(
        status: String = "trial",
        isPremium: Boolean = false,
        trialEndsAt: String = "2026-10-02T12:00:00.000Z",
        trialDaysLeft: Int = 14,
        plansUsed: Int = 0,
        plansLimit: Int? = 1,
        chatUsed: Int = 1,
        chatLimit: Int? = 2,
        chatResetsAt: String = "2026-09-19T00:00:00.000Z",
    ): String = buildString {
        append("{\"success\":true,\"data\":{")
        append("\"status\":\"").append(status).append("\",")
        append("\"is_premium\":").append(isPremium).append(",")
        append("\"trial_ends_at\":\"").append(trialEndsAt).append("\",")
        append("\"trial_days_left\":").append(trialDaysLeft).append(",")
        append("\"plans\":{\"used\":").append(plansUsed).append(",")
        append("\"limit\":").append(plansLimit?.toString() ?: "null").append("},")
        append("\"chat\":{\"used\":").append(chatUsed).append(",")
        append("\"limit\":").append(chatLimit?.toString() ?: "null").append(",")
        append("\"resets_at\":\"").append(chatResetsAt).append("\"}}}")
    }

    /** A premium entitlement: both limits `null`, `is_premium` true, no trial days left. */
    fun premiumEntitlementBody(): String = entitlement(
        status = "premium",
        isPremium = true,
        trialDaysLeft = 0,
        plansUsed = 3,
        plansLimit = null,
        chatUsed = 12,
        chatLimit = null,
    )

    // ---------------------------------------------------------------------------------------
    // Errors — the 402 quota family. These are the only errors carrying `code` AND `data`.
    // ---------------------------------------------------------------------------------------

    /**
     * 402 from `requirePlanQuota`. `data` is `{used, limit}` — no `resets_at`.
     *
     * The client parses only `success`/`code`/`message` (`BillingErrorDto`), so `data` is an
     * unknown block here. That is exactly what makes it worth sending: it proves the error path
     * tolerates fields it does not model.
     */
    fun planLimitReached(used: Int = 1, limit: Int = 1): String = buildString {
        append("{\"success\":false,\"code\":\"PLAN_LIMIT_REACHED\",")
        append("\"message\":\"Your free plan includes one generated routine.\",")
        append("\"data\":{\"used\":").append(used).append(",\"limit\":").append(limit).append("}}")
    }

    /** 402 from `requireChatQuota`. `data` is `{used, limit, resets_at}` — always UTC midnight. */
    fun chatQuotaExceeded(
        used: Int = 2,
        limit: Int = 2,
        resetsAt: String = "2026-09-19T00:00:00.000Z",
    ): String = buildString {
        append("{\"success\":false,\"code\":\"CHAT_QUOTA_EXCEEDED\",")
        append("\"message\":\"You have used today's coach messages.\",")
        append("\"data\":{\"used\":").append(used).append(",\"limit\":").append(limit).append(",")
        append("\"resets_at\":\"").append(resetsAt).append("\"}}")
    }

    /**
     * 402 from `requireActiveAccess`. `data` is `{trial_ends_at}` only — **no** `used`/`limit`.
     *
     * Every quota-gated route stacks `requireActiveAccess` before the quota guards, so an expired
     * user always gets this and never `PLAN_LIMIT_REACHED`, even when both would apply.
     */
    fun trialExpired(trialEndsAt: String = "2026-07-01T00:00:00.000Z"): String = buildString {
        append("{\"success\":false,\"code\":\"TRIAL_EXPIRED\",")
        append("\"message\":\"Your free trial has ended. Subscribe to keep training.\",")
        append("\"data\":{\"trial_ends_at\":\"").append(trialEndsAt).append("\"}}")
    }

    // ---------------------------------------------------------------------------------------
    // Errors — uncoded, message-only. `code` and `data` are ABSENT, not null.
    // ---------------------------------------------------------------------------------------

    /** The generic envelope: no `code`, no `data`. Everything outside billing and 402 uses this. */
    fun error(message: String): String = "{\"success\":false,\"message\":\"$message\"}"

    /**
     * A 2xx whose `data` block is missing entirely.
     *
     * Not something the backend sends deliberately, but the client's DTOs all default `data` to
     * null, so every caller has a branch for it and each one should be exercised.
     */
    fun successWithNoData(): String = "{\"success\":true}"

    /** A failure envelope carrying neither `message` nor `code` — the bare minimum. */
    fun failureWithNoMessage(): String = "{\"success\":false}"

    /**
     * Wraps a raw `data` block in the success envelope.
     *
     * For the handful of contract tests that need a deliberately odd `data` payload — an extra
     * field, an explicit null, a missing required key — where the point of the test is the
     * payload, not the envelope around it.
     */
    fun envelope(dataJson: String): String = "{\"success\":true,\"data\":$dataJson}"

    /** 401 from the `protect` middleware when no Authorization header is sent. */
    fun unauthorized(): String = error("Not authorized, no token")

    /** 401 from `POST /api/auth/login` on a bad password. */
    fun invalidCredentials(): String = error("Invalid credentials")

    /** 401 when the email belongs to a Google account with no local password. */
    fun googleAccountHint(): String =
        error("This account uses Google Sign-In. Continue with Google.")

    /** 400 from `POST /api/auth/register` on a duplicate email. */
    fun userAlreadyExists(): String = error("User already exists with that email")

    /** 404 from `GET /api/plans/active` when the user has no plan yet. */
    fun noActivePlan(): String = error("No active workout plan found")

    /** 422 from `POST /api/ai/import-plan` when Gemini returned no weeks. */
    fun planNotRecognized(): String = error(
        "We couldn't read a training plan in what you sent. " +
            "Try a clearer document, or paste the routine as text.",
    )

    /** 400 from `POST /api/ai/import-plan` on a rejected payload. */
    fun importRejected(
        message: String = "sourceText must be at most 20000 characters",
    ): String = error(message)

    /** 409 from `POST /api/billing/verify` when the token belongs to another account. */
    fun tokenAlreadyClaimed(): String = buildString {
        append("{\"success\":false,\"code\":\"TOKEN_ALREADY_CLAIMED\",")
        append("\"message\":\"This purchase token is already linked to another account.\"}")
    }

    /**
     * What Render and Cloudflare answer with on a gateway error: HTML, not JSON.
     *
     * The three entitlement guards end in `next(error)` and the backend registers no error
     * handler, so a guard failure also falls through to Express's default HTML handler.
     */
    fun htmlGatewayError(): String = "<html>Bad Gateway</html>"
}
