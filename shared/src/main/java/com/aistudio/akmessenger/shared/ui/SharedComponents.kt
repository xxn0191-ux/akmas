package com.aistudio.akmessenger.shared.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.akmessenger.shared.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AkFonts {
    val Outfit = FontFamily(Font(R.font.outfit, FontWeight.Normal))
    val PlusJakarta = FontFamily(Font(R.font.plus_jakarta_sans, FontWeight.Normal))
    val SpaceGrotesk = FontFamily(Font(R.font.space_grotesk, FontWeight.Normal))
    val JetBrainsMono = FontFamily(Font(R.font.jetbrains_mono, FontWeight.Normal))
}

@Composable
fun VerifiedBadge(
    isVerified: Boolean,
    size: Dp = 16.dp,
    tint: Color = Color(0xFF1DA1F2)
) {
    if (isVerified) {
        Icon(
            imageVector = Icons.Filled.Verified,
            contentDescription = "Verified account",
            tint = tint,
            modifier = Modifier.size(size)
        )
    }
}

@Composable
fun NameWithBadge(
    name: String,
    isVerified: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight = FontWeight.SemiBold
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name.ifBlank { "AK User" },
            color = color,
            fontWeight = fontWeight,
            maxLines = 1
        )
        if (isVerified) {
            Spacer(modifier = Modifier.width(4.dp))
            VerifiedBadge(isVerified = true, size = 16.dp)
        }
    }
}

@Composable
fun UserAvatar(
    name: String,
    avatarBase64: String,
    size: Dp = 48.dp,
    isOnline: Boolean = false,
    accentColor: Color = Color(0xFF00A884),
    modifier: Modifier = Modifier
) {
    val bitmap = remember(avatarBase64) {
        if (avatarBase64.isBlank()) null
        else try {
            val bytes = Base64.decode(avatarBase64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    val initials = remember(name) {
        name.trim().split(" ").filter { it.isNotBlank() }
            .take(2).joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { "AK" }
    }

    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.20f))
                .border(1.5.dp, accentColor.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "$name avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = initials,
                    color = accentColor,
                    fontFamily = AkFonts.Outfit,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.36f).sp
                )
            }
        }
        if (isOnline) {
            Box(
                modifier = Modifier
                    .size(13.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 1.dp, y = 1.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF25D366))
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}

object AkTimeFormatter {
    fun formatClock(ts: Long): String {
        if (ts <= 0L) return ""
        return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(ts))
    }

    fun formatDateTime(ts: Long): String {
        if (ts <= 0L) return ""
        return SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(Date(ts))
    }

    fun formatDuration(sec: Long): String {
        val m = sec / 60
        val s = sec % 60
        return "%02d:%02d".format(m, s)
    }

    fun formatLastSeen(isOnline: Boolean, lastSeen: Long): String {
        if (isOnline) return "Online"
        if (lastSeen <= 0L) return "Offline"
        return "Last seen ${formatDateTime(lastSeen)}"
    }
}
