package com.bluescope.ai.bluetooth.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Centralises the Android-version permission matrix used by the scanner and GATT client. */
object BluetoothPermissionPolicy {
    fun scanPermissions(sdkInt: Int = Build.VERSION.SDK_INT): Set<String> =
        if (sdkInt >= Build.VERSION_CODES.S) {
            setOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            setOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun connectPermissions(sdkInt: Int = Build.VERSION.SDK_INT): Set<String> =
        if (sdkInt >= Build.VERSION_CODES.S) {
            setOf(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            emptySet()
        }

    fun missingScanPermissions(
        context: Context,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): Set<String> = missing(context, scanPermissions(sdkInt))

    fun missingConnectPermissions(
        context: Context,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): Set<String> = missing(context, connectPermissions(sdkInt))

    private fun missing(context: Context, permissions: Set<String>): Set<String> =
        permissions.filterTo(linkedSetOf()) { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
}
