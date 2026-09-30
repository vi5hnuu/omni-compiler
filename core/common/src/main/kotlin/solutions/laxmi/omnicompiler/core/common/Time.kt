package solutions.laxmi.omnicompiler.core.common

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant

/** Abstracted so time- and id-dependent logic stays deterministic under test. */
interface TimeSource {
    fun now(): Instant
}

interface IdGenerator {
    fun newId(): String
}

internal class SystemTimeSource @Inject constructor() : TimeSource {
    override fun now(): Instant = Clock.System.now()
}

internal class UuidGenerator @Inject constructor() : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}

@Module
@InstallIn(SingletonComponent::class)
internal interface TimeModule {
    @Binds
    fun bindsTimeSource(impl: SystemTimeSource): TimeSource

    @Binds
    fun bindsIdGenerator(impl: UuidGenerator): IdGenerator
}
