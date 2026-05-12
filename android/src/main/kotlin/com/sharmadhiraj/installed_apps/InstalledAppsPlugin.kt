package com.sharmadhiraj.installed_apps

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import android.util.Log
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import android.widget.Toast.LENGTH_SHORT
import androidx.core.net.toUri
import com.sharmadhiraj.installed_apps.Util.Companion.convertAppToMap
import com.sharmadhiraj.installed_apps.Util.Companion.getLaunchablePackageNames
import com.sharmadhiraj.installed_apps.Util.Companion.getPackageInfo
import com.sharmadhiraj.installed_apps.Util.Companion.getPackageManager
import com.sharmadhiraj.installed_apps.Util.Companion.isSystemApp
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import java.util.Locale.ENGLISH

class InstalledAppsPlugin : MethodCallHandler, FlutterPlugin, ActivityAware {

    private lateinit var channel: MethodChannel
    private var applicationContext: Context? = null
    private var activityContext: Context? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        applicationContext = binding.applicationContext
        channel = MethodChannel(binding.binaryMessenger, "installed_apps")
        channel.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        applicationContext = null
    }

    override fun onAttachedToActivity(activityPluginBinding: ActivityPluginBinding) {
        activityContext = activityPluginBinding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activityContext = null
    }

    override fun onReattachedToActivityForConfigChanges(activityPluginBinding: ActivityPluginBinding) {
        activityContext = activityPluginBinding.activity
    }

    override fun onDetachedFromActivity() {
        activityContext = null
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        val context = currentContext()
        if (context == null) {
            result.error("ERROR", "Context is null", null)
            return
        }
        when (call.method) {
            "getInstalledApps" -> {
                val excludeSystemApps = call.argument<Boolean>("exclude_system_apps") ?: true
                val excludeNonLaunchableApps =
                    call.argument<Boolean>("exclude_non_launchable_apps") ?: true
                val withIcon = call.argument<Boolean>("with_icon") ?: false
                val packageNamePrefix = call.argument<String>("package_name_prefix") ?: ""
                val platformTypeName = call.argument<String>("platform_type") ?: ""

                Thread {
                    try {
                        val apps: List<Map<String, Any?>> = getInstalledApps(
                            context,
                            excludeSystemApps,
                            excludeNonLaunchableApps,
                            withIcon,
                            packageNamePrefix,
                            PlatformType.fromString(platformTypeName),
                        )
                        mainHandler.post { result.success(apps) }
                    } catch (e: Exception) {
                        Log.w("InstalledAppsPlugin", "getInstalledApps: ${e.message}")
                        mainHandler.post {
                            result.error("ERROR", "Failed to get installed apps", e.message)
                        }
                    }
                }.start()
            }

            "startApp" -> {
                val packageName = call.argument<String>("package_name")
                result.success(startApp(context, packageName))
            }

            "openSettings" -> {
                val packageName = call.argument<String>("package_name")
                result.success(openSettings(context, packageName))
            }

            "toast" -> {
                val message = call.argument<String>("message") ?: ""
                val short = call.argument<Boolean>("short_length") ?: true
                toast(context, message, short)
                result.success(null)
            }

            "getAppInfo" -> {
                val packageName = call.argument<String>("package_name") ?: ""
                result.success(getAppInfo(getPackageManager(context), packageName))
            }

            "isSystemApp" -> {
                val packageName = call.argument<String>("package_name") ?: ""
                result.success(isSystemApp(getPackageInfo(context, packageName)))
            }

            "uninstallApp" -> {
                val packageName = call.argument<String>("package_name") ?: ""
                result.success(uninstallApp(context, packageName))
            }

            "isAppInstalled" -> {
                val packageName = call.argument<String>("package_name") ?: ""
                result.success(isAppInstalled(context, packageName))
            }

            else -> result.notImplemented()
        }
    }

    private fun currentContext(): Context? = activityContext ?: applicationContext

    private fun getInstalledApps(
        context: Context,
        excludeSystemApps: Boolean,
        excludeNonLaunchableApps: Boolean,
        withIcon: Boolean,
        packageNamePrefix: String,
        platformType: PlatformType?
    ): List<Map<String, Any?>> {
        val packageManager = getPackageManager(context)
        var packageInfos = packageManager.getInstalledPackages(0)

        if (excludeSystemApps) {
            packageInfos =
                packageInfos.filter { packageInfo -> !isSystemApp(packageInfo) }
        }
        val launchablePackageNames = getLaunchablePackageNames(packageManager)
        if (excludeNonLaunchableApps) {
            packageInfos = packageInfos.filter { packageInfo ->
                launchablePackageNames.contains(packageInfo.packageName)
            }
        }
        if (packageNamePrefix.isNotEmpty()) {
            val prefixLower = packageNamePrefix.lowercase(ENGLISH)
            packageInfos = packageInfos.filter { packageInfo ->
                packageInfo.packageName.lowercase(ENGLISH).startsWith(prefixLower)
            }
        }

        val appsWithPlatformType = if (platformType != null) {
            packageInfos.mapNotNull { packageInfo ->
                val detectedPlatformType = PlatformTypeUtil.getPlatform(
                    packageManager,
                    packageInfo.applicationInfo
                )
                if (detectedPlatformType == platformType.value) {
                    packageInfo to detectedPlatformType
                } else {
                    null
                }
            }
        } else {
            packageInfos.map { it to null }
        }

        return appsWithPlatformType
            .filter { (packageInfo, _) -> packageInfo.applicationInfo != null }
            .map { (packageInfo, detectedPlatformType) ->
                convertAppToMap(
                    packageManager,
                    packageInfo,
                    withIcon,
                    isSystemAppOverride = if (excludeSystemApps) false else null,
                    isLaunchableOverride = launchablePackageNames.contains(packageInfo.packageName),
                    platformTypeOverride = detectedPlatformType,
                )
            }
    }


    private fun startApp(context: Context, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return try {
            val launchIntent = getPackageManager(context).getLaunchIntentForPackage(packageName)
                ?: return false
            launchIntent.addFlags(FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            true
        } catch (e: Exception) {
            Log.w("InstalledAppsPlugin", "startApp: ${e.message}")
            false
        }
    }

    private fun toast(context: Context, text: String, short: Boolean) {
        Toast.makeText(
            context,
            text,
            if (short) LENGTH_SHORT else LENGTH_LONG
        ).show()
    }

    private fun openSettings(context: Context, packageName: String?): Boolean {
        if (!isAppInstalled(context, packageName)) {
            Log.d("InstalledAppsPlugin", "App $packageName is not installed on this device.")
            return false
        }
        val intent = Intent().apply {
            flags = FLAG_ACTIVITY_NEW_TASK
            action = ACTION_APPLICATION_DETAILS_SETTINGS
            data = Uri.fromParts("package", packageName, null)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w("InstalledAppsPlugin", "openSettings: ${e.message}")
            false
        }
    }

    private fun getAppInfo(
        packageManager: PackageManager,
        packageName: String
    ): Map<String, Any?>? {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            convertAppToMap(
                packageManager,
                packageInfo,
                true
            )
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun uninstallApp(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = "package:$packageName".toUri()
                flags = FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w("InstalledAppsPlugin", "uninstallApp: ${e.message}")
            false
        }
    }

    private fun isAppInstalled(context: Context, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val packageManager: PackageManager = getPackageManager(context)
        return try {
            packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w("InstalledAppsPlugin", "isAppInstalled: ${e.message}")
            false
        }
    }

}
