package com.rocketlauncher.domain.model

import androidx.annotation.StringRes
import com.rocketlauncher.R

/**
 * Как показывать избранные (звёзды) в общем списке чатов.
 */
enum class FavoriteDisplayMode(@StringRes val labelRes: Int) {
    INLINE_IN_GROUPS(R.string.favorite_inline),
    SEPARATE_GROUP(R.string.favorite_separate)
}
