package com.hsxy.oplusx

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.highcapable.yukihookapi.hook.factory.dataChannel
import com.hsxy.oplusx.hook.systemui.ProbeContract

private object ProbeState {
    var report by mutableStateOf("尚未收到诊断报告。启用模块并将 SystemUI 加入作用域后，可点击“采集 SystemUI”。")
}

class MainActivity : ComponentActivity() {
    private val channel by lazy { applicationContext.dataChannel("com.android.systemui") }
    private var mediaIconEnabled by mutableStateOf(true)
    private var batteryMaskEnabled by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadSettings()
        channel.wait<String>(ProbeContract.RESULT_VIEW_TREE) { value ->
            runOnUiThread { ProbeState.report = value }
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("OPlusX", style = MaterialTheme.typography.headlineLarge)
                        Text("Stage 1 · 状态栏工具箱", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "目标：OnePlus 13 / Android 15 / ColorOS 15.0.0.821。功能由 LSPosed 注入 SystemUI，设置可独立开关。",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("状态栏功能", style = MaterialTheme.typography.titleLarge)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                        Text("媒体输出设备图标", style = MaterialTheme.typography.titleMedium)
                                        Text("显示扬声器、蓝牙或有线音频设备图标，跟随输出路由更新；不显示文字。", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Switch(
                                        checked = mediaIconEnabled,
                                        onCheckedChange = { enabled ->
                                            mediaIconEnabled = enabled
                                            saveSettings()
                                        }
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                        Text("电池右侧黑色遮罩", style = MaterialTheme.typography.titleMedium)
                                        Text("在电池图形内部右侧叠加小遮罩，不改变原图标尺寸和状态栏布局参数。", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Switch(
                                        checked = batteryMaskEnabled,
                                        onCheckedChange = { enabled ->
                                            batteryMaskEnabled = enabled
                                            saveSettings()
                                        }
                                    )
                                }
                                Text(
                                    "如果状态栏没有立即更新，请重启 SystemUI 或重启手机。关闭功能后无需卸载模块。",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { channel.put(ProbeContract.REQUEST_VIEW_TREE) }) {
                                Text("采集 SystemUI")
                            }
                            OutlinedButton(onClick = { copyReport() }) {
                                Text("复制诊断")
                            }
                        }
                        Text("诊断报告", style = MaterialTheme.typography.titleMedium)
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = ProbeState.report,
                                modifier = Modifier.padding(14.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }

    private fun loadSettings() {
        runCatching {
            val result = contentResolver.call(
                Uri.parse(OPlusXSettingsProvider.SETTINGS_URI.toString()),
                "getSettings",
                null,
                null
            )
            mediaIconEnabled = result?.getBoolean(OPlusXSettingsProvider.KEY_MEDIA_ICON, true) ?: true
            batteryMaskEnabled = result?.getBoolean(OPlusXSettingsProvider.KEY_BATTERY_MASK, true) ?: true
        }
    }

    private fun saveSettings() {
        val extras = Bundle().apply {
            putBoolean(OPlusXSettingsProvider.KEY_MEDIA_ICON, mediaIconEnabled)
            putBoolean(OPlusXSettingsProvider.KEY_BATTERY_MASK, batteryMaskEnabled)
        }
        runCatching {
            contentResolver.call(
                OPlusXSettingsProvider.SETTINGS_URI,
                "setSettings",
                null,
                extras
            )
        }
    }

    private fun copyReport() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("OPlusX Probe", ProbeState.report))
    }
}
