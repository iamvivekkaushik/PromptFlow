package com.vivekkaushik.promptflow.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp

/** 24-grid design icon (spec §05), tinted flat — decorative, labels carry semantics. */
@Composable
fun PFIcon(@DrawableRes id: Int, size: Dp, tint: Color, modifier: Modifier = Modifier) {
    Icon(painterResource(id), contentDescription = null, tint = tint, modifier = modifier.size(size))
}
