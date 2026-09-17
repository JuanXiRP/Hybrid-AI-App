package com.example.hybrid_ai_app.core.domain.model

/**
 * The backend read the document the user supplied but found no training program in it (HTTP 422).
 *
 * Typed rather than message-based for the same reason as [PremiumRequiredException]: the UI has to
 * pick localized copy, and the server's message is English-only. This one is user-fixable — attach
 * a clearer file or paste the routine as text — so the screen keeps the user on the import step
 * instead of failing the whole onboarding.
 */
class PlanNotRecognizedException : Exception("No training plan could be read from the material")

/**
 * The backend rejected the payload itself (HTTP 400) — unsupported file type, too many files, or
 * nothing to read. [message] is the server's explanation, which is specific enough to show.
 */
class PlanImportRejectedException(override val message: String) : Exception(message)
