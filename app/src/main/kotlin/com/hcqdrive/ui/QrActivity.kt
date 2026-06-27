package com.hcqdrive.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.wifi.WifiManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hcqdrive.auth.AuthService
import com.hcqdrive.qr.QrGenerator
import com.hcqdrive.service.HcqDriveService
import com.hcqdrive.ui.theme.HcqDriveTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class QrActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HcqDriveTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    QrScreen(
                        serviceAddress = computeAddress(),
                        onRefreshCode = { AuthService.generateCode() },
                    )
                }
            }
        }
    }

    private fun computeAddress(): String {
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val ip = wifi?.connectionInfo?.ipAddress ?: 0
        val str = if (ip == 0) "0.0.0.0" else
            "%d.%d.%d.%d".format(ip and 0xFF, (ip shr 8) and 0xFF, (ip shr 16) and 0xFF, (ip shr 24) and 0xFF)
        return "http://$str:${HcqDriveService.DEFAULT_PORT}"
    }
}

@Composable
private fun QrScreen(
    serviceAddress: String,
    onRefreshCode: () -> String,
) {
    var code by remember { mutableStateOf(AuthService.currentCodeAndExpiry()?.first ?: onRefreshCode()) }
    var bitmap: Bitmap? by remember { mutableStateOf(buildQrBitmap(serviceAddress, code)) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (isActive) {
            delay(15_000L)
            val active = AuthService.currentCodeAndExpiry()
            if (active == null) {
                code = onRefreshCode()
            } else {
                code = active.first
            }
            bitmap = buildQrBitmap(serviceAddress, code)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 360.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "HcqDrive",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF4338CA),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "扫码访问此设备",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6B7280),
            )
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = bitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "QR code",
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(text = "QR 生成中…", color = Color(0xFF6B7280))
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = "配对码",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6B7280),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatCode(code),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF1F2937),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = serviceAddress,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6B7280),
            )
        }
    }
}

private fun formatCode(code: String): String =
    if (code.length == 6) "${code.substring(0, 3)} ${code.substring(3)}" else code

private fun buildQrBitmap(url: String, code: String): Bitmap? {
    return try {
        val payload = "$url?code=$code"
        QrGenerator.generate(payload, size = 512, margin = 1)
    } catch (e: Exception) {
        null
    }
}
