package solutions.laxmi.omnicompiler.core.data.execution

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Sends offline-queued runs as soon as the device is back online, even if the app was closed. */
@HiltWorker
internal class RunQueueWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val executions: ExecutionRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = if (executions.sendPendingRuns()) Result.success() else Result.retry()
}

internal class RunQueueScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    fun schedule() {
        val request = OneTimeWorkRequestBuilder<RunQueueWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    private companion object {
        const val WORK_NAME = "omni-run-queue"
    }
}
