package com.hsxy.oplusx

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
    var report by mutableStateOf("尚未收到诊断报告。请先在 LSPosed 中启用 OPlusX，并将作用域设置为 SystemUI。")
}

class MainActivity : ComponentActivity() {
    private val channel by lazy { applicationContext.dataChannel("com.android.systemui") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                        Text("Stage 0 · SystemUI 诊断工具", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "目标：OnePlus 13 / Android 15 / ColorOS 15.0.0.821。当前版本只读取状态栏视图树和媒体输出路由，不修改系统界面。",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { channel.put(ProbeContract.REQUEST_VIEW_TREE) }) {
                                Text("采集 SystemUI")
                            }
                            OutlinedButton(onClick = { copyReport() }) {
                                Text("复制报告")
                            }
                        }
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

    private fun copyReport() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("OPlusX Probe", ProbeState.report))
    }
}
