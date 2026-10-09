package solutions.laxmi.omnicompiler.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import solutions.laxmi.omnicompiler.core.model.User

/** The name to show for [this] user: guests have only a generated username, so they read as "Guest". */
@Composable
fun User.shownName(): String =
    if (isGuest) stringResource(R.string.common_guest) else displayName.ifBlank { stringResource(R.string.common_your_account) }

/** Avatar initials matching [shownName]. */
@Composable
fun User.shownInitials(): String = if (isGuest) stringResource(R.string.common_guest).take(1).uppercase() else initials
