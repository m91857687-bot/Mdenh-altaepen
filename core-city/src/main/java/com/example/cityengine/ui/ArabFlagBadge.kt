package com.example.cityengine.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.cityengine.model.ArabCountriesRegistry
import com.example.cityengine.model.CountryFlagStyle

/**
 * Shared 22-country Arab flag badge displaying the verified bundled VectorDrawable
 * (with proportional 3:2 frame and subtle border so white stripes remain crisp on light themes).
 */
@Composable
fun ArabFlagBadge(
    flag: CountryFlagStyle,
    modifier: Modifier = Modifier,
    width: Dp = 36.dp,
    height: Dp = 24.dp,
    cornerRadius: Dp = 6.dp
) {
    val resolvedRes = flag.drawableResId ?: ArabCountriesRegistry.drawableResFor(flag.countryCode)
    val shape = RoundedCornerShape(cornerRadius)
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(shape)
            .border(0.8.dp, borderColor, shape)
    ) {
        if (resolvedRes != null) {
            Image(
                painter = painterResource(id = resolvedRes),
                contentDescription = flag.countryName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (flag.isVerticalStripes) {
            Row(modifier = Modifier.fillMaxSize()) {
                flag.stripeColors.forEach { hex ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color(hex))
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                flag.stripeColors.forEach { hex ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(hex))
                    )
                }
            }
        }
    }
}
