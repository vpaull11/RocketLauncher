package com.rocketlauncher.data.share

import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton

/**
 * URIs из «Поделиться» — передаются в открытый чат после выбора комнаты в списке.
 */
@Singleton
class ShareUploadQueue @Inject constructor() {
    private val lock = Any()
    private var pending: List<Uri> = emptyList()

    @Synchronized
    fun setPending(uris: List<Uri>) {
        pending = uris.filter { it != Uri.EMPTY }
    }

    @Synchronized
    fun pollPending(): List<Uri> {
        val u = pending
        pending = emptyList()
        return u
    }

    @Synchronized
    fun hasPending(): Boolean = pending.isNotEmpty()
}
