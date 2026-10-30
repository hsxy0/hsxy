package com.hsxy.oplusx.hook.systemui

import android.content.Context
import android.database.ContentObserver
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.content.res.ColorStateList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Adds optional status-bar features without changing the dimensions or layout params
 * of the stock battery view. Configuration is read from the companion app provider.
 */
class StatusBarFeatureController(
    private val context: Context,
    private val mainHandler: Handler
) {
    companion object {
        private const val AUTHORITY = "content://com.hsxy.oplusx.config"
        private val SETTINGS_URI: Uri = Uri.parse("$AUTHORITY/settings")
        private const val METHOD_GET = "getSettings"
        private const val KEY_MEDIA_ICON = "media_icon"
        private const val KEY_BATTERY_MASK = "battery_mask"
    }

    private var root: ViewGroup? = null
    private var systemIcons: ViewGroup? = null
    private var batteryIcon: ImageView? = null
    private var mediaIcon: ImageView? = null
    private var mask: BatteryMaskDrawable? = null
    private var mediaEnabled = true
    private var maskEnabled = true
    private var routeName = "媒体输出设备"
    private val observerRegistered = AtomicBoolean(false)
    private val audioCallbackRegistered = AtomicBoolean(false)
    private val routeDrawable by lazy { MediaRouteDrawable(context.resources.displayMetrics.density) }

    fun attach(statusBarRoot: ViewGroup) {
        root = statusBarRoot
        installViews()
        registerSettingsObserver()
        registerAudioCallback()
        readSettings()
        applyVisibility()
        updateMask()
        updateMediaRoute()
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    private fun findId(name: String): Int =
        context.resources.getIdentifier(name, "id", "com.android.systemui")

    private fun installViews() {
        val currentRoot = root ?: return
        val iconsId = findId("system_icons")
        val batteryId = findId("battery")
        val batteryIconId = findId("battery_icon_view")
        val icons = if (iconsId != 0) currentRoot.findViewById<ViewGroup>(iconsId) else null
        val battery = if (batteryId != 0) currentRoot.findViewById<View>(batteryId) else null
        val batteryImage = if (batteryIconId != 0) currentRoot.findViewById<ImageView>(batteryIconId) else null
        systemIcons = icons
        batteryIcon = batteryImage

        if (icons != null && battery != null) {
            var icon = mediaIcon
            if (icon == null || icon.parent !== icons) {
                icon = ImageView(context).apply {
                    contentDescription = routeName
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setPadding(dp(2), dp(2), dp(2), dp(2))
                    imageTintList = null
                    setImageDrawable(routeDrawable)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                }
                val params = LinearLayout.LayoutParams(dp(22), dp(22)).apply {
                    marginStart = dp(2)
                    marginEnd = dp(3)
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                val index = icons.indexOfChild(battery).let { if (it < 0) icons.childCount else it }
                if (icons is LinearLayout) {
                    icons.addView(icon, index, params)
                } else {
                    icons.addView(icon, index, ViewGroup.LayoutParams(dp(22), dp(22)))
                }
                mediaIcon = icon
            }
        }

        if (batteryImage != null) {
            batteryImage.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                updateMaskBounds()
            }
            updateMask()
        }
    }

    private fun registerSettingsObserver() {
        if (!observerRegistered.compareAndSet(false, true)) return
        runCatching {
            context.contentResolver.registerContentObserver(
                SETTINGS_URI,
                false,
                object : ContentObserver(mainHandler) {
                    override fun onChange(selfChange: Boolean) {
                        readSettings()
                        applyVisibility()
                        updateMask()
                    }
                }
            )
        }.onFailure {
            observerRegistered.set(false)
        }
    }

    private fun registerAudioCallback() {
        if (!audioCallbackRegistered.compareAndSet(false, true)) return
        runCatching {
            val audio = context.getSystemService(AudioManager::class.java) ?: return
            audio.registerAudioDeviceCallback(object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                    updateMediaRoute()
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                    updateMediaRoute()
                }
            }, mainHandler)
        }.onFailure {
            audioCallbackRegistered.set(false)
        }
    }

    private fun readSettings() {
        val result = runCatching {
            context.contentResolver.call(Uri.parse(AUTHORITY), METHOD_GET, null, null)
        }.getOrNull()
        mediaEnabled = result?.getBoolean(KEY_MEDIA_ICON, true) ?: true
        maskEnabled = result?.getBoolean(KEY_BATTERY_MASK, true) ?: true
    }

    private fun applyVisibility() {
        mediaIcon?.visibility = if (mediaEnabled) View.VISIBLE else View.GONE
    }

    private fun updateMediaRoute() {
        val currentRoot = root ?: return
        val audio = currentRoot.context.getSystemService(AudioManager::class.java)
        val route = runCatching {
            val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
            audio?.getAudioDevicesForAttributes(attributes)?.firstOrNull()
        }.getOrNull()
        val deviceType = route?.type ?: AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        routeName = route?.productName?.toString()?.takeIf { it.isNotBlank() } ?: routeLabel(deviceType)
        routeDrawable.setRoute(deviceType)
        val tint = batteryIcon?.imageTintList?.defaultColor ?: Color.WHITE
        routeDrawable.setColor(tint)
        mediaIcon?.contentDescription = "媒体输出设备：$routeName"
        mediaIcon?.visibility = if (mediaEnabled) View.VISIBLE else View.GONE
        mediaIcon?.invalidate()
    }

    private fun routeLabel(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "手机扬声器"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "听筒"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> "蓝牙音频设备"
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "有线耳机"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB 音频设备"
        else -> "媒体输出设备"
    }

    private fun updateMask() {
        val image = batteryIcon ?: return
        val existing = mask
        if (!maskEnabled) {
            if (existing != null) image.overlay.remove(existing)
            mask = null
            return
        }
        val drawable = existing ?: BatteryMaskDrawable(context.resources.displayMetrics.density).also {
            mask = it
            image.overlay.add(it)
        }
        updateMaskBounds()
        drawable.invalidateSelf()
    }

    private fun updateMaskBounds() {
        val image = batteryIcon ?: return
        val drawable = mask ?: return
        val width = dp(4)
        val height = dp(6)
        val left = (image.width - dp(7)).coerceAtLeast(0)
        val top = ((image.height - height) / 2).coerceAtLeast(0)
        drawable.setBounds(left, top, left + width, top + height)
    }

    private class BatteryMaskDrawable(density: Float) : Drawable() {
        private val radius = 1.5f * density
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.FILL }
        override fun draw(canvas: Canvas) {
            val b = bounds
            canvas.drawRoundRect(RectF(b), radius, radius, paint)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }

    private class MediaRouteDrawable(density: Float) : Drawable() {
        private val stroke = 1.8f * density
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.WHITE
        }
        private var routeType = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER

        fun setRoute(type: Int) {
            routeType = type
            invalidateSelf()
        }

        fun setColor(color: Int) {
            paint.color = color
            invalidateSelf()
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val save = canvas.save()
            canvas.translate(b.left.toFloat(), b.top.toFloat())
            val scale = minOf(b.width(), b.height()).toFloat() / 24f
            canvas.scale(scale, scale)
            if (isHeadset(routeType)) drawHeadset(canvas) else drawSpeaker(canvas)
            canvas.restoreToCount(save)
        }

        private fun isHeadset(type: Int): Boolean = when (type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET -> true
            else -> false
        }

        private fun drawSpeaker(canvas: Canvas) {
            val speaker = Path().apply {
                moveTo(3.5f, 9f); lineTo(7.5f, 9f); lineTo(12.5f, 5f)
                lineTo(12.5f, 19f); lineTo(7.5f, 15f); lineTo(3.5f, 15f); close()
            }
            val old = paint.style
            paint.style = Paint.Style.FILL
            canvas.drawPath(speaker, paint)
            paint.style = Paint.Style.STROKE
            canvas.drawArc(RectF(10f, 7f, 19f, 17f), -55f, 110f, false, paint)
            canvas.drawArc(RectF(9f, 3.5f, 23f, 20.5f), -52f, 104f, false, paint)
            paint.style = old
        }

        private fun drawHeadset(canvas: Canvas) {
            canvas.drawArc(RectF(4f, 3f, 20f, 21f), 185f, 170f, false, paint)
            canvas.drawRoundRect(RectF(3.5f, 11f, 7.5f, 18f), 1.4f, 1.4f, paint)
            canvas.drawRoundRect(RectF(16.5f, 11f, 20.5f, 18f), 1.4f, 1.4f, paint)
            if (routeType == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                routeType == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                routeType == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                routeType == AudioDeviceInfo.TYPE_BLE_SPEAKER) {
                val bt = Path().apply {
                    moveTo(12f, 7f); lineTo(16f, 10.5f); lineTo(9f, 16.5f)
                    moveTo(12f, 7f); lineTo(12f, 18f); lineTo(16f, 14.5f); lineTo(9f, 8.5f)
                }
                paint.strokeWidth = stroke * 0.75f
                canvas.drawPath(bt, paint)
                paint.strokeWidth = stroke
            }
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }
}
