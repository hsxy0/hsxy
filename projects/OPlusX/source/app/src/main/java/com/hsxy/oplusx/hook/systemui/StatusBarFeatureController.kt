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
    private var lastRouteKey = ""
    private val routeRefreshStarted = AtomicBoolean(false)
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
        startRouteRefresh()
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
                val index = 0
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

    private fun startRouteRefresh() {
        if (!routeRefreshStarted.compareAndSet(false, true)) return
        val refresh = object : Runnable {
            override fun run() {
                updateMediaRoute()
                mainHandler.postDelayed(this, 2500L)
            }
        }
        mainHandler.postDelayed(refresh, 2500L)
    }

    private fun updateMediaRoute() {
        val currentRoot = root ?: return
        val audio = currentRoot.context.getSystemService(AudioManager::class.java)
        val route = runCatching {
            val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
            audio?.getAudioDevicesForAttributes(attributes)?.firstOrNull()
        }.getOrNull()
        val deviceType = route?.type ?: AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        val resolvedName = route?.productName?.toString()?.takeIf { it.isNotBlank() } ?: routeLabel(deviceType)
        val routeKey = deviceType.toString() + "|" + resolvedName
        if (routeKey == lastRouteKey) return
        lastRouteKey = routeKey
        routeName = resolvedName
        routeDrawable.setRoute(deviceType, resolvedName)
        routeDrawable.setColor(resolveStatusBarIconTint())
        mediaIcon?.contentDescription = "媒体输出设备：$routeName"
        mediaIcon?.visibility = if (mediaEnabled) View.VISIBLE else View.GONE
        mediaIcon?.invalidate()
    }

    private fun resolveStatusBarIconTint(): Int {
        // ColorOS may render dark or light status icons depending on wallpaper/theme.
        // Prefer live tint from a neighboring SystemUI icon, rather than hard-coding white.
        fun findTint(view: View?): Int? {
            if (view == null) return null
            if (view is ImageView) {
                view.imageTintList?.defaultColor?.let { return it }
                view.drawable?.let { drawable ->
                    if (android.os.Build.VERSION.SDK_INT >= 29) {
                        drawable.colorFilter?.let { filter ->
                            // ColorFilter is opaque; don't guess a color from it.
                        }
                    }
                }
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) findTint(view.getChildAt(i))?.let { return it }
            }
            return null
        }
        val icons = systemIcons
        val wifiId = findId("wifi_signal")
        val mobileId = findId("mobile_signal")
        return findTint(if (wifiId != 0) icons?.findViewById(wifiId) else null)
            ?: findTint(if (mobileId != 0) icons?.findViewById(mobileId) else null)
            ?: batteryIcon?.imageTintList?.defaultColor
            ?: Color.BLACK
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
        val glyph = image.drawable?.bounds
        val glyphLeft = glyph?.left ?: 0
        val glyphRight = glyph?.right?.takeIf { it > glyphLeft } ?: image.width
        val glyphTop = glyph?.top ?: 0
        val glyphBottom = glyph?.bottom?.takeIf { it > glyphTop } ?: image.height
        val width = dp(3)
        val height = dp(4)
        // Keep the mask a little inside the actual battery silhouette; never anchor to
        // the ImageView's outer edge, which can include large transparent side margins.
        val left = (glyphRight - width - dp(3)).coerceAtLeast(glyphLeft)
        val top = (glyphTop + (glyphBottom - glyphTop - height) / 2).coerceAtLeast(0)
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
        private var productName = ""

        fun setRoute(type: Int, name: String = "") {
            routeType = type
            productName = name.lowercase()
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
            when {
                !isHeadset(routeType) -> drawSpeaker(canvas)
                productName.contains("eyewear") || productName.contains("眼镜") -> drawSmartGlasses(canvas)
                productName.contains("airpods") || productName.contains("freebuds 5") || productName.contains("半入耳") -> drawOpenFitBuds(canvas)
                productName.contains("wh-1000") || productName.contains("xm3") || productName.contains("头戴") -> drawOverEarHeadphones(canvas)
                else -> drawInEarBuds(canvas)
            }
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

        private fun drawInEarBuds(canvas: Canvas) {
            // Two compact stemmed in-ear earbuds, matching the first recommended icon family.
            val left = Path().apply {
                moveTo(5f, 5f); cubicTo(2.5f, 5f, 2.5f, 9f, 5f, 10f)
                lineTo(7f, 12f); lineTo(7f, 19f); lineTo(9.5f, 19f); lineTo(9.5f, 11f)
                cubicTo(9.5f, 9f, 7f, 8f, 7f, 6f); close()
            }
            val right = Path().apply {
                moveTo(16f, 5f); cubicTo(13.5f, 5f, 13.5f, 9f, 16f, 10f)
                lineTo(18f, 12f); lineTo(18f, 19f); lineTo(20.5f, 19f); lineTo(20.5f, 11f)
                cubicTo(20.5f, 9f, 18f, 8f, 18f, 6f); close()
            }
            val old = paint.style
            paint.style = Paint.Style.FILL
            canvas.drawPath(left, paint)
            canvas.drawPath(right, paint)
            paint.style = old
        }

        private fun drawOpenFitBuds(canvas: Canvas) {
            val old = paint.style
            paint.style = Paint.Style.STROKE
            canvas.drawArc(RectF(3f, 4f, 12f, 14f), 185f, 170f, false, paint)
            canvas.drawArc(RectF(12f, 4f, 21f, 14f), 185f, 170f, false, paint)
            canvas.drawRoundRect(RectF(5f, 8f, 9f, 15f), 1.8f, 1.8f, paint)
            canvas.drawRoundRect(RectF(15f, 8f, 19f, 15f), 1.8f, 1.8f, paint)
            paint.style = old
        }

        private fun drawOverEarHeadphones(canvas: Canvas) {
            canvas.drawArc(RectF(3f, 2.5f, 21f, 21.5f), 185f, 170f, false, paint)
            canvas.drawRoundRect(RectF(2.5f, 10f, 7f, 18f), 1.5f, 1.5f, paint)
            canvas.drawRoundRect(RectF(17f, 10f, 21.5f, 18f), 1.5f, 1.5f, paint)
            canvas.drawLine(5f, 13f, 5f, 16f, paint)
            canvas.drawLine(19f, 13f, 19f, 16f, paint)
        }

        private fun drawSmartGlasses(canvas: Canvas) {
            canvas.drawRoundRect(RectF(2.5f, 7f, 10.5f, 15f), 2f, 2f, paint)
            canvas.drawRoundRect(RectF(13.5f, 7f, 21.5f, 15f), 2f, 2f, paint)
            canvas.drawLine(10.5f, 9f, 13.5f, 9f, paint)
            canvas.drawLine(2.5f, 9f, 1f, 7.5f, paint)
            canvas.drawLine(21.5f, 9f, 23f, 7.5f, paint)
            canvas.drawArc(RectF(6f, 15f, 18f, 22f), 10f, 160f, false, paint)
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
