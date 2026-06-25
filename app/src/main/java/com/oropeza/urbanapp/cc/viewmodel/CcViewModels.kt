package com.oropeza.urbanapp.cc.viewmodel

import androidx.lifecycle.ViewModel
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.data.local.CcSession
import com.oropeza.urbanapp.asd.data.local.CcEvent
import kotlinx.coroutines.flow.Flow

class CcListVM : ViewModel() {
    val sessions = AsdGraph.repo.ccSessionsFlow
}

class CcNewSessionVM : ViewModel() {
    suspend fun getLatestCcSession() = AsdGraph.repo.getLatestCcSession()
    suspend fun createCcSession(session: CcSession) = AsdGraph.repo.createCcSession(session)
}

class CcDetailVM : ViewModel() {
    fun ccSessionFlow(sessionId: Long): Flow<CcSession?> = AsdGraph.repo.ccSessionFlow(sessionId)
    fun ccEventsFlow(sessionId: Long): Flow<List<CcEvent>> = AsdGraph.repo.ccEventsFlow(sessionId)
    suspend fun nextCcSeq(sessionId: Long) = AsdGraph.repo.nextCcSeq(sessionId)
    suspend fun addCcEvent(event: CcEvent) = AsdGraph.repo.addCcEvent(event)
    suspend fun endCcSession(sessionId: Long) = AsdGraph.repo.endCcSession(sessionId)
}
