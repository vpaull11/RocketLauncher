package com.rocketlauncher.data.repository

/**
 * Ошибка пересылки с текстом для пользователя ([message]) и полным отчётом для поддержки ([debugReport]).
 */
class ForwardMessageDebugException(
    override val message: String,
    val debugReport: String,
) : Exception(message)
