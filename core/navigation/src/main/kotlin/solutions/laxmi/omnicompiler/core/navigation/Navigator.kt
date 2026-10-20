package solutions.laxmi.omnicompiler.core.navigation

/** Navigation commands available to screens; implemented once by the app around the back stack. */
interface Navigator {
    fun navigate(route: Route)
    fun back()

    /** Clears the stack and shows [route] as the new root (e.g. after sign-in or sign-out). */
    fun resetTo(route: Route)

    /** Replaces the top destination (e.g. switching projects in the editor). */
    fun replace(route: Route)

    /**
     * Opens [detail] from the list screen [from]. Whatever that list opened before is replaced, so on large screens,
     * where list and detail show side by side, picking item after item swaps the detail instead of stacking them.
     */
    fun openDetail(from: Route, detail: Route)

    /**
     * The detail currently open from the list screen [list] (see [openDetail]), or null. Read in composition it
     * updates as details open and close, so a list beside its detail can mark the picked item.
     */
    fun openedFrom(list: Route): Route?

    /**
     * Closes [route] and whatever was opened from it. A list screen's own Back uses this: beside its detail on a large
     * screen, plain [back] would only close the detail.
     */
    fun exit(route: Route)
}
