package com.sharmadhiraj.installed_apps

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import java.util.zip.ZipFile

class PlatformTypeUtil {

    companion object {

        fun getPlatform(packageManager: PackageManager, applicationInfo: ApplicationInfo?): String {
            if (applicationInfo == null) return "unknown"
            val packageName = applicationInfo.packageName.lowercase()

            val packageInfo = try {
                packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            } catch (_: PackageManager.NameNotFoundException) {
                return "unknown"
            }

            packageInfo.activities?.forEach { activity ->
                val name = activity.name.lowercase()
                when {
                    name.contains("io.flutter.embedding") -> return "flutter"
                    name.contains("com.facebook.react") -> return "react_native"
                    name.contains("mono.android") -> return "xamarin"
                    name.contains("capacitor") || name.contains("cordova") -> return "ionic"
                }
            }

            val metaData = try {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.GET_META_DATA
                )?.metaData
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }

            metaData?.let {
                if (it.containsKey("io.flutter.app.FlutterApplication")) return "flutter"
                if (it.containsKey("com.getcapacitor.BridgeActivity")) return "ionic"
            }

            return scanApkForPlatform(applicationInfo?.sourceDir)
        }

        private fun scanApkForPlatform(apkPath: String?): String {
            if (apkPath.isNullOrEmpty()) return "unknown"
            return try {
                val zipFile = try {
                    ZipFile(apkPath)
                } catch (e: java.util.zip.ZipException) {
                    Log.w("InstalledAppsPlugin", "Invalid APK zip: ${e.message}")
                    return "unknown"
                }
                zipFile.use { apk ->
                    apk.entries()
                        .asSequence()
                        .map { it.name }
                        .forEach { entryName ->
                            when {
                                entryName.contains("/flutter_assets/") -> return "flutter"
                                entryName.contains("react_native_routes.json") ||
                                    entryName.contains("libs_reactnativecore_components") ||
                                    entryName.contains("node_modules_reactnative") -> {
                                    return "react_native"
                                }

                                entryName.contains("libaot-Xamarin") -> return "xamarin"
                                entryName.contains("node_modules_ionic") -> return "ionic"
                            }
                        }
                    "native_or_others"
                }
            } catch (e: Exception) {
                Log.w("InstalledAppsPlugin", "getPlatform: ${e.message}")
                "unknown"
            }
        }

    }
}
