package solutions.laxmi.omnicompiler.core.network.auth

import solutions.laxmi.omnicompiler.core.datastore.StoredSession
import solutions.laxmi.omnicompiler.core.datastore.StoredUser
import solutions.laxmi.omnicompiler.core.network.dto.AuthTokensDto
import solutions.laxmi.omnicompiler.core.network.dto.AuthUserDto

internal fun AuthTokensDto.toStoredSession(nowEpochMs: Long, previousUser: StoredUser?): StoredSession = StoredSession(
    accessToken = accessToken,
    refreshToken = refreshToken,
    accessExpiresAtEpochMs = nowEpochMs + expiresInSeconds * 1_000,
    user = user?.toStoredUser() ?: previousUser,
)

internal fun AuthUserDto.toStoredUser() = StoredUser(
    id = id,
    accountType = accountType,
    provider = authProvider,
    email = email,
    username = username,
    firstName = firstName,
    lastName = lastName,
    profileUrl = profileUrl,
    verified = enabled,
    createdAt = createdAt,
)
