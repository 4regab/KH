package com.kisskh.tv.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

/** Corner shapes. Cards and tiles share a family so rows look even. */
@Immutable
data class KhShapes(
    val field: CornerBasedShape = RoundedCornerShape(8.dp),
    val button: CornerBasedShape = RoundedCornerShape(8.dp),
    val card: CornerBasedShape = RoundedCornerShape(8.dp),
    val tile: CornerBasedShape = RoundedCornerShape(12.dp),
    /** Fully rounded ends, for filter chips and tabs. */
    val chip: CornerBasedShape = RoundedCornerShape(percent = 50),
)
