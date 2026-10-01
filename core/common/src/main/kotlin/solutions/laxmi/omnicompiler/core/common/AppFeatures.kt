package solutions.laxmi.omnicompiler.core.common

/**
 * Features that ship in the code but are switched per build. The app module provides the instance, so a release can
 * hold a feature back without removing it.
 */
data class AppFeatures(
    /** Usage & plan and API key & webhooks screens; held back from Play releases until they are ready. */
    val accountTools: Boolean,
)
