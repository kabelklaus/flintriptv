package com.flintr.tv.testutil

import com.flintr.tv.data.local.Credentials
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow

/**
 * StateFlow-Attrappe für credentialsFlow in Service-Tests: emittiert nie.
 *
 * Die Services starten in init einen collect auf Dispatchers.IO, der bei
 * null-Credentials den Zustand asynchron auf leer zurücksetzt. Das würde mit
 * per Reflection gesetztem Test-Zustand racen (Ursache der bekannten
 * Test-Flakiness). Mit dieser Attrappe bleibt der Init-Pfad vollständig inert;
 * das Laden wird in den Tests explizit über syncIfNeeded() getrieben.
 */
val silentCredentialsFlow: StateFlow<Credentials?> = object : StateFlow<Credentials?> {
    override val value: Credentials? = null
    override val replayCache: List<Credentials?> = emptyList()
    override suspend fun collect(collector: FlowCollector<Credentials?>): Nothing {
        awaitCancellation()
    }
}

