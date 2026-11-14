package com.aistudio.akmessenger.admin.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.akmessenger.admin.R
import com.aistudio.akmessenger.admin.ui.theme.AdminBlack
import com.aistudio.akmessenger.admin.ui.theme.AdminNavy
import com.aistudio.akmessenger.admin.ui.theme.NeonCyan
import com.aistudio.akmessenger.shared.ui.AkFonts
import kotlinx.coroutines.delay

@Composable
fun AdminSplashScreen(
    onFinished: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.75f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "admin_splash"
    )
    LaunchedEffect(Unit) {
        visible = true
        delay(850)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AdminBlack, AdminNavy, AdminBlack)))
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.scale(scale)) {
            Image(
                painter = painterResource(id = R.drawable.ic_ak_admin_fg),
                contentDescription = "AK Messenger Admin Logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(104.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .border(2.dp, NeonCyan.copy(alpha = 0.65f), RoundedCornerShape(26.dp))
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "AK MESSENGER ADMIN",
                fontFamily = AkFonts.SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                letterSpacing = 2.sp,
                color = Color.White
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "DIRECT COMMAND CENTER • REALTIME CONTROL",
                fontFamily = AkFonts.JetBrainsMono,
                fontSize = 11.sp,
                color = NeonCyan
            )
        }
    }
}
