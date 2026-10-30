package com.hsxy.oplusx

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

/**
 * Small cross-process settings bridge. SystemUI reads the two feature flags through
 * ContentResolver.call(); changes are broadcast through a ContentObserver notification.
 */
class OPlusXSettingsProvider : ContentProvider() {
    companion object {
        const val AUTHORITY = "com.hsxy.oplusx.config"
        const val KEY_MEDIA_ICON = "media_icon"
        const val KEY_BATTERY_MASK = "battery_mask"
        private const val PREFS = "oplusx_settings"
        private const val DEFAULT_MEDIA_ICON = true
        private const val DEFAULT_BATTERY_MASK = true
        val SETTINGS_URI: Uri = Uri.parse("content://$AUTHORITY/settings")
    }

    override fun onCreate(): Boolean = context != null

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return Bundle()
        val preferences = ctx.getSharedPreferences(PREFS, 0)
        return when (method) {
            "getSettings" -> Bundle().apply {
                putBoolean(KEY_MEDIA_ICON, preferences.getBoolean(KEY_MEDIA_ICON, DEFAULT_MEDIA_ICON))
                putBoolean(KEY_BATTERY_MASK, preferences.getBoolean(KEY_BATTERY_MASK, DEFAULT_BATTERY_MASK))
            }
            "setSettings" -> {
                val mediaIcon = extras?.getBoolean(KEY_MEDIA_ICON, DEFAULT_MEDIA_ICON) ?: DEFAULT_MEDIA_ICON
                val batteryMask = extras?.getBoolean(KEY_BATTERY_MASK, DEFAULT_BATTERY_MASK) ?: DEFAULT_BATTERY_MASK
                preferences.edit()
                    .putBoolean(KEY_MEDIA_ICON, mediaIcon)
                    .putBoolean(KEY_BATTERY_MASK, batteryMask)
                    .apply()
                ctx.contentResolver.notifyChange(SETTINGS_URI, null)
                Bundle().apply {
                    putBoolean(KEY_MEDIA_ICON, mediaIcon)
                    putBoolean(KEY_BATTERY_MASK, batteryMask)
                }
            }
            else -> super.call(method, arg, extras)
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
