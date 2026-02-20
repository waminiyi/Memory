package com.memory.sotopatrick.ui.presentation.discovery

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*

@Composable
fun PermissionHandler(
    onPermissionsResult: (Boolean) -> Unit
) {
    // Define permissions based on API level
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Android 12+ (API 31+)
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        // Android 6.0 to 11 (API 23-30)
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultMap ->
        val allGranted = resultMap.values.all { it }
        onPermissionsResult(allGranted)
    }

    // Trigger request on first launch
    LaunchedEffect(Unit) {
        launcher.launch(permissionsToRequest.toTypedArray())
    }
}
