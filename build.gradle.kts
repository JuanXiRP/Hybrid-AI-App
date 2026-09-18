// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.kover) apply false
}

/**
 * Rule configuration passed straight to ktlint.
 *
 * These live here rather than only in .editorconfig because that is what actually takes effect:
 * `ktlint_standard_package-name = disabled` in .editorconfig worked, but
 * `ktlint_standard_function-naming = disabled` did not (verified with --rerun-tasks), so relying
 * on editorconfig resolution for the load-bearing settings is not dependable. .editorconfig keeps
 * the same values for the IDE's benefit.
 *
 * Every entry here switches off a rule ktlint cannot fix by itself. That matters more than it
 * sounds: an unfixable violation makes `spotlessApply` exit non-zero, so the format gate becomes
 * permanently unsatisfiable rather than merely noisy. Anything requiring a human edit belongs in
 * the separate `lintDebug` step, not in the formatter.
 *
 *  - package-name: the package is `com.example.hybrid_ai_app`; the underscore is deliberate (see
 *    the applicationId note in app/build.gradle.kts) and renaming it would touch every file for
 *    no functional gain. Permanently inapplicable, not debt.
 *  - function-naming: all 17 violations are @Composable functions, which are PascalCase by Compose
 *    convention. `ktlint_function_naming_ignore_when_annotated_with` is the documented exemption
 *    but had no effect here.
 *  - no-wildcard-imports (47 across 20 files) and max-line-length (88 lines): real debt. Turning
 *    either back on means fixing the occurrences first, in its own commit.
 */
val ktlintRules = mapOf(
    "ktlint_standard_package-name" to "disabled",
    "ktlint_standard_function-naming" to "disabled",
    "ktlint_standard_no-wildcard-imports" to "disabled",
    "max_line_length" to "off",
)

// Formatting gate. Deliberately limited to what ktlint can FIX on its own, so `spotlessApply`
// always converges and `spotlessCheck` is always satisfiable — see .editorconfig for the two
// rules we switch off and why.
spotless {
    kotlin {
        target("app/src/**/*.kt")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(ktlintRules)
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.kts", "app/*.kts")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(ktlintRules)
        trimTrailingWhitespace()
        endWithNewline()
    }
}
