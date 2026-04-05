package com.rocketlauncher.data.repository

/**
 * Ошибка загрузки файла с кратким текстом и полным отчётом для копирования.
 */
class FileUploadDebugException(
    override val message: String,
    val debugReport: String,
) : Exception(message)
