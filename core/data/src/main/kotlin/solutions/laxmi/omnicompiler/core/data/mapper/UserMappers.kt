package solutions.laxmi.omnicompiler.core.data.mapper

import solutions.laxmi.omnicompiler.core.datastore.StoredUser
import solutions.laxmi.omnicompiler.core.model.AccountType
import solutions.laxmi.omnicompiler.core.model.AuthProvider
import solutions.laxmi.omnicompiler.core.model.User
import kotlin.time.Instant

internal fun StoredUser.toModel() = User(
    id = id,
    accountType = if (accountType.equals("GUEST", true)) AccountType.GUEST else AccountType.USER,
    provider = AuthProvider.entries.firstOrNull { it.name.equals(provider, true) } ?: AuthProvider.LOCAL,
    email = email,
    username = username,
    firstName = firstName,
    lastName = lastName,
    profileUrl = profileUrl,
    verified = verified,
    createdAt = createdAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
)

internal fun User.toStored() = StoredUser(
    id = id,
    accountType = accountType.name,
    provider = provider.name,
    email = email,
    username = username,
    firstName = firstName,
    lastName = lastName,
    profileUrl = profileUrl,
    verified = verified,
    createdAt = createdAt?.toString(),
)
