package solutions.laxmi.omnicompiler.core.navigation

/** Navigation commands available to screens; implemented once by the app around the back stack. */
interface Navigator {
    fun navigate(route: Route)
    fun back()

    /** Clears the stack and shows [route] as the new root (e.g. after sign-in or sign-out). */
    fun resetTo(route: Route)

    /** Replaces the top destination (e.g. switching projects in the editor). */
    fun replace(route: Route)
}
