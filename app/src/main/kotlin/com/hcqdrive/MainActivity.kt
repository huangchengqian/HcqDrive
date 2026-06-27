package com.hcqdrive

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.hcqdrive.auth.AuthService
import com.hcqdrive.qr.QrGenerator
import com.hcqdrive.service.HcqDriveService
import com.hcqdrive.ui.QrActivity
import com.hcqdrive.ui.theme.HcqDriveTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class MainActivity : ComponentActivity() {

    private var isServiceRunning by mutableStateOf(false)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> ensureStorageAndStart() }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> startHcqDriveService() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HcqDriveTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HcqDriveScreen(
                        isServiceRunning = isServiceRunning,
                        serviceAddress = serviceAddress(),
                        onToggleService = ::onToggleService,
                        onShowQr = ::onShowQr,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    private fun onShowQr() {
        AuthService.generateCode()
        val intent = Intent(this, QrActivity::class.java)
        startActivity(intent)
    }

    private fun onToggleService() {
        if (isServiceRunning) {
            stopHcqDriveService()
        } else {
            ensureNotificationPermissionAndStart()
        }
    }

    private fun ensureNotificationPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        ensureStorageAndStart()
    }

    private fun ensureStorageAndStart() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_MEDIA_IMAGES
                ) != PackageManager.PERMISSION_GRANTED
            ) perms += Manifest.permission.READ_MEDIA_IMAGES
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_MEDIA_VIDEO
                ) != PackageManager.PERMISSION_GRANTED
            ) perms += Manifest.permission.READ_MEDIA_VIDEO
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_MEDIA_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) perms += Manifest.permission.READ_MEDIA_AUDIO
        } else {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) perms += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (perms.isEmpty()) {
            startHcqDriveService()
        } else {
            storagePermissionLauncher.launch(perms.toTypedArray())
        }
    }

    private fun startHcqDriveService() {
        val intent = Intent(this, HcqDriveService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        isServiceRunning = true
    }

    private fun stopHcqDriveService() {
        val intent = Intent(this, HcqDriveService::class.java)
        stopService(intent)
        isServiceRunning = false
    }

    private fun serviceAddress(): String {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
        val ip = if (ipInt == 0) {
            "0.0.0.0"
        } else {
            "%d.%d.%d.%d".format(
                ipInt and 0xFF,
                (ipInt shr 8) and 0xFF,
                (ipInt shr 16) and 0xFF,
                (ipInt shr 24) and 0xFF,
            )
        }
        return "http://$ip:${HcqDriveService.DEFAULT_PORT}"
    }
}

@Composable
private fun HcqDriveScreen(
    isServiceRunning: Boolean,
    serviceAddress: String,
    onToggleService: () -> Unit,
    onShowQr: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pairCode by remember { mutableStateOf(AuthService.currentCodeAndExpiry()?.first ?: AuthService.generateCode()) }
    var qrBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var connections by remember { mutableStateOf(AuthService.pairedCount()) }

    LaunchedEffect(isServiceRunning, serviceAddress, pairCode) {
        if (isServiceRunning) {
            qrBitmap = runCatching { QrGenerator.generate("$serviceAddress?code=$pairCode", 360) }
                .getOrNull()
        } else {
            qrBitmap = null
        }
    }

    LaunchedEffect(isServiceRunning) {
        while (isActive && isServiceRunning) {
            delay(5_000L)
            AuthService.currentCodeAndExpiry()?.let { (c, _) -> pairCode = c }
            connections = AuthService.pairedCount()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "HcqDrive",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (isServiceRunning) "服务运行中 · 已连接 $connections 台" else "服务未启动",
                style = MaterialTheme.typography.titleMedium,
                color = if (isServiceRunning) Color(0xFF16A34A) else Color(0xFF6B7280),
            )
            if (isServiceRunning) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "配对码",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF6B7280),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = formatCode(pairCode),
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF1F2937),
                        )
                        Spacer(Modifier.height(16.dp))
                        val bmp = qrBitmap
                        if (bmp != null) {
                            Box(
                                modifier = Modifier
                                    .size(220.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "QR",
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(220.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF3F4F6)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "QR 不可用",
                                    color = Color(0xFF6B7280),
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = serviceAddress,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF6B7280),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onShowQr) {
                        Text("大屏配对码")
                    }
                    OutlinedButton(onClick = { pairCode = AuthService.generateCode() }) {
                        Text("刷新配对码")
                    }
                }
            } else {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "启动后,同 WiFi 设备通过浏览器访问此地址,输入 6 位配对码即可使用。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B7280),
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onToggleService,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .heightIn(min = 52.dp),
            ) {
                Text(
                    text = if (isServiceRunning) "停止服务" else "启动服务",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

private fun formatCode(code: String): String =
    if (code.length == 6) "${code.substring(0, 3)} ${code.substring(3)}" else code
