package com.rocketlauncher.domain.usecase

import com.rocketlauncher.data.repository.MessageRepository
import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(roomId: String, text: String): Result<Unit> =
        messageRepository.sendMessage(roomId, text)
}
