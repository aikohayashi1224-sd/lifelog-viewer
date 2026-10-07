package jp.sd.lifelogapp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object BackgroundTasks {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
