package com.rocketlauncher.domain.usecase

import com.rocketlauncher.data.realtime.RealtimeMessageService
import com.rocketlauncher.data.repository.RoomRepository
import javax.inject.Inject

/**
 * Подтягивает список комнат и подписок с сервера и обновляет подписки WebSocket на новые комнаты.
 */
class SyncChatsFromServerUseCase @Inject constructor(
    private val roomRepository: RoomRepository,
    private val realtimeMessageService: RealtimeMessageService
) {
    suspend operator fun invoke() {
        roomRepository.syncRooms()
        val ids = runCatching { roomRepository.getAllRoomIdsMergedWithSubscriptions() }
            .getOrElse { roomRepository.getAllRoomIds() }
        if (ids.isNotEmpty()) {
            realtimeMessageService.subscribeAll(ids)
        }
    }
}
