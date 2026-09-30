package solutions.laxmi.omnicompiler.core.data

import solutions.laxmi.omnicompiler.core.model.AppError

/** Carries an [AppError] through APIs that can only report Throwables (Paging LoadState). */
class AppErrorException(val error: AppError) : Exception(error.message)
