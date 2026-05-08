package com.illumination.operator.engine

object OperatorEngineRuntime {
    private val lock = Any()
    private var client: OperatorAppServerClient? = null

    fun start(
        appFilesDir: String,
        codexHome: String? = null,
        workspaceRoot: String? = null,
    ): OperatorEngineStartResult = synchronized(lock) {
        client?.let { existing ->
            return@synchronized OperatorEngineStartResult.Started(existing)
        }

        when (val start = OperatorAppServerClient.start(appFilesDir, codexHome, workspaceRoot)) {
            is OperatorEngineStartResult.Started -> {
                client = start.client
                start
            }
            is OperatorEngineStartResult.Failed -> start
        }
    }

    fun currentClient(): OperatorAppServerClient? = synchronized(lock) {
        client
    }

    fun shutdown() {
        val clientToShutdown = synchronized(lock) {
            client.also { client = null }
        }
        clientToShutdown?.shutdown()
    }
}
