package ai.koog.utils.io

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * A [kotlinx.coroutines.CoroutineDispatcher] that is suitable for IO operations.
 * Delegates to [Dispatchers.IO] on native targets.
 */
public actual val Dispatchers.SuitableForIO: CoroutineDispatcher
    get() = Dispatchers.IO
