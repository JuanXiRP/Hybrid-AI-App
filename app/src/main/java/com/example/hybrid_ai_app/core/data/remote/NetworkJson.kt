package com.example.hybrid_ai_app.core.data.remote

import kotlinx.serialization.json.Json

/**
 * The single JSON configuration for everything that crosses the network boundary.
 *
 * It lives here rather than inline in `NetworkModule` so tests can decode with the *same*
 * instance the Retrofit converter uses. The wire contract with the backend is hand-mirrored — no
 * codegen — so a test that builds its own `Json` can pass while production fails, which defeats
 * the point of having contract tests at all.
 *
 * `ignoreUnknownKeys = true` is load-bearing: the backend adds fields (Mongoose emits `__v` and a
 * per-subdocument `_id` on every array element) that no client DTO declares.
 *
 * Everything else is left at kotlinx defaults, and the defaults are part of the contract:
 *  - `encodeDefaults = false` — a property equal to its default is omitted from the request body.
 *    This is how `ProfileUpdateRequest` keeps `last_period_date` out of the payload for
 *    non-female users.
 *  - `coerceInputValues = false` — an explicit `null` for a non-nullable property throws rather
 *    than silently falling back to the default.
 */
val NetworkJson: Json = Json { ignoreUnknownKeys = true }
