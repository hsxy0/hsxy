package com.hsxy.oplusx.hook.systemui

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import com.github.kyuubiran.ezxhelper.params
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class StatusBarProbeHook : YukiBaseHooker() {
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val rootRef = AtomicReference<WeakReference<ViewGroup>?>(null)
    private val listenerRegistered = AtomicBoolean(false)

    override fun onHook() {
        if (packageName != "com.android.systemui") return

        runCatching {
            "com.android.systemui.statusbar.phone.fragment.CollapsedStatusBarFragment"
                .toClass()
                .resolve()
                .firstMethod {
                    name = "onViewCreated"
                    params(View::class.java, Bundle::class.java)
                }
                .hook {
                    after {
                        val root = args.getOrNull(0) as? ViewGroup ?: return@after
                        rootRef.set(WeakReference(root))
                        registerProbeRequest()
                        YLog.info("OPlusX captured status bar root: " + root.javaClass.name)
                    }
                }
        }.onFailure {
            YLog.error("OPlusX failed to hook CollapsedStatusBarFragment", it)
        }
    }

    private fun registerProbeRequest() {
        if (!listenerRegistered.compareAndSet(false, true)) return
        dataChannel.wait(ProbeContract.REQUEST_VIEW_TREE) {
            val root = rootRef.get()?.get()
            if (root == null) {
                dataChannel.put(ProbeContract.RESULT_VIEW_TREE, "Status bar root is not ready. Reopen the probe and retry.")
                return@wait
            }
            mainHandler.post {
                val report = buildString {
                    appendLine("OPlusX SystemUI Probe")
                    appendLine("Process: com.android.systemui")
                    appendLine("Root: " + root.javaClass.name)
                    appendLine()
                    appendLine("=== Status bar view tree ===")
                    dumpTree(root, root, this, 0, intArrayOf(0))
                    appendLine()
                    appendLine("=== Expected media output route (USAGE_MEDIA) ===")
                    appendLine(readMediaRoute(root))
                }
                dataChannel.put(ProbeContract.RESULT_VIEW_TREE, report)
            }
        }
    }

    private fun dumpTree(view: View, root: View, out: StringBuilder, depth: Int, count: IntArray) {
        if (depth > 9 || count[0] >= 450) return
        count[0]++
        val idName = runCatching {
            if (view.id == View.NO_ID) "no-id" else view.resources.getResourceName(view.id)
        }.getOrDefault("id-" + view.id)
        val rootPosition = IntArray(2)
        val position = IntArray(2)
        root.getLocationOnScreen(rootPosition)
        view.getLocationOnScreen(position)
        val visibility = when (view.visibility) {
            View.VISIBLE -> "VISIBLE"
            View.INVISIBLE -> "INVISIBLE"
            View.GONE -> "GONE"
            else -> "UNKNOWN"
        }
        out.append("  ".repeat(depth))
            .append(idName).append(" | ")
            .append(view.javaClass.name)
            .append(" | x=").append(position[0] - rootPosition[0])
            .append(" y=").append(position[1] - rootPosition[1])
            .append(" w=").append(view.width)
            .append(" h=").append(view.height)
            .append(" visibility=").append(visibility)
            .appendLine()

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                dumpTree(view.getChildAt(index), root, out, depth + 1, count)
            }
        }
    }

    private fun readMediaRoute(root: View): String = runCatching {
        val audioManager = root.context.getSystemService(AudioManager::class.java)
            ?: return "AudioManager unavailable"
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()
        val routes = audioManager.getAudioDevicesForAttributes(attributes)
        if (routes.isEmpty()) return "No route returned."
        routes.joinToString("\n") { device ->
            val type = when (device.type) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "BUILTIN_SPEAKER"
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "BUILTIN_EARPIECE"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BLUETOOTH_A2DP"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BLUETOOTH_SCO"
                AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE_HEADSET"
                AudioDeviceInfo.TYPE_BLE_SPEAKER -> "BLE_SPEAKER"
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "WIRED_HEADPHONES"
                AudioDeviceInfo.TYPE_USB_HEADSET -> "USB_HEADSET"
                else -> "TYPE_" + device.type
            }
            type + " | name=" + device.productName + " | id=" + device.id
        }
    }.getOrElse { error ->
        "Route query failed: " + error.javaClass.simpleName + ": " + error.message
    }
}
