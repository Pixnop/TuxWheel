package io.github.tuxwheel.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.tuxwheel.R
import kotlinx.coroutines.delay

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DashboardGauge(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
    actions: DashboardActions? = null
) {
    val context = LocalContext.current
    val renderer = remember(context) { DashboardCanvasRenderer(context) }
    val latest by rememberUpdatedState(state)
    var geometry by remember { mutableStateOf<DashboardGeometry?>(null) }
    var frame by remember { mutableIntStateOf(0) }
    val gestures = remember(context, actions) {
        DashboardGestures(context, { latest }, { geometry },
            { actions?.singleTap() },
            { actions?.doubleTap() }, { block, data -> actions?.replaceBlock(block, data) })
    }
    DisposableEffect(gestures) { onDispose { gestures.cancel() } }
    LaunchedEffect(state) {
        do {
            frame++
            delay(30)
        } while (renderer.animating)
    }
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()
            .onSizeChanged { geometry = renderer.geometry(it.width.toFloat(), it.height.toFloat(), latest) }
            .pointerInteropFilter { gestures.touch(it) }) {
            frame
            geometry = renderer.geometry(size.width, size.height, state)
            drawIntoCanvas { renderer.draw(it.nativeCanvas, size.width, size.height, state) }
        }
        if (actions != null) DashboardQuickControls(state, actions)
    }
}

/** Quick wheel lock (top-left) and speed limiter (top-centre) toggles drawn over the gauge. */
@Composable
private fun BoxScope.DashboardQuickControls(state: DashboardUiState, actions: DashboardActions) {
    LaunchedEffect(state.lockSupported) {
        if (state.lockSupported) actions.refreshLockState()
    }
    val tint = Color(ContextCompat.getColor(LocalContext.current, R.color.wheelview_text))
    if (state.lockSupported) {
        IconButton(
            onClick = { actions.toggleLock(state) },
            modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(48.dp)
        ) {
            Icon(
                imageVector = if (state.wheelLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = stringResource(
                    if (state.wheelLocked) R.string.wheel_lock_status_locked else R.string.wheel_lock_status_unlocked
                ),
                tint = tint,
                modifier = Modifier.size(32.dp)
            )
        }
    }
    if (state.speedLimiterAvailable || state.speedLimiterEnabled) {
        IconButton(
            onClick = { actions.toggleSpeedLimiter() },
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(48.dp)
        ) {
            Image(
                painter = painterResource(
                    if (state.speedLimiterEnabled) R.drawable.ic_speed_limit else R.drawable.ic_speed_limit_off
                ),
                contentDescription = stringResource(
                    if (state.speedLimiterEnabled) R.string.speed_limiter_on else R.string.speed_limiter_off
                ),
                modifier = Modifier.size(36.dp)
                    .alpha(if (state.speedLimiterAvailable) 1f else 0.5f)
            )
        }
    }
}
