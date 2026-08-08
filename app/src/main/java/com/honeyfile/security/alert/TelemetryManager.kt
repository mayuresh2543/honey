package com.honeyfile.security.alert

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.net.Inet4Address
import java.net.NetworkInterface

data class DeviceTelemetry(
    val latitude: Double?,
    val longitude: Double?,
    val googleMapsUrl: String?,
    val ipAddress: String,
    val wifiSsid: String,
    val batteryPercentage: Int,
    val isCharging: Boolean,
    val formattedSummary: String
)

class TelemetryManager(private val context: Context) {

    fun getDeviceTelemetry(): DeviceTelemetry {
        return try {
            val location = getBestKnownLocation()
            val lat = location?.latitude
            val lng = location?.longitude
            val mapsUrl = if (lat != null && lng != null) "https://maps.google.com/?q=$lat,$lng" else null

            val ip = getLocalIpAddress()
            val ssid = getWifiSSID()
            val (batteryLevel, isCharging) = getBatteryInfo()

            val summaryBuilder = StringBuilder()
            summaryBuilder.append("📍 Location: ")
            if (mapsUrl != null) {
                summaryBuilder.append("$lat, $lng ($mapsUrl)")
            } else {
                summaryBuilder.append("GPS location unavailable")
            }
            summaryBuilder.append("\n🌐 Network: IP=$ip | Network=$ssid")
            summaryBuilder.append("\n🔋 Battery: $batteryLevel% ${if (isCharging) "(⚡ Charging)" else "(Discharging)"}")

            val summary = summaryBuilder.toString()
            Log.d(TAG, "Captured intruder telemetry:\n$summary")

            DeviceTelemetry(
                latitude = lat,
                longitude = lng,
                googleMapsUrl = mapsUrl,
                ipAddress = ip,
                wifiSsid = ssid,
                batteryPercentage = batteryLevel,
                isCharging = isCharging,
                formattedSummary = summary
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error generating device telemetry safely", e)
            DeviceTelemetry(
                latitude = null,
                longitude = null,
                googleMapsUrl = null,
                ipAddress = "127.0.0.1",
                wifiSsid = "Cellular / Mobile Data",
                batteryPercentage = 100,
                isCharging = false,
                formattedSummary = "📍 Telemetry: GPS unavailable | IP: 127.0.0.1"
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun getBestKnownLocation(): Location? {
        return try {
            val hasFine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!hasFine && !hasCoarse) return null

            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
            val providers = locationManager.getProviders(true)
            var bestLocation: Location? = null

            for (provider in providers) {
                val l = locationManager.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                    bestLocation = l
                }
            }
            bestLocation
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching location", e)
            null
        }
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching IP address", e)
        }
        return "127.0.0.1"
    }

    private fun getWifiSSID(): String {
        try {
            val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

            if (connectivityManager != null) {
                val activeNetwork = connectivityManager.activeNetwork
                val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

                if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    val wifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        capabilities.transportInfo as? WifiInfo
                    } else {
                        @Suppress("DEPRECATION")
                        wifiManager?.connectionInfo
                    }

                    val ssid = wifiInfo?.ssid?.replace("\"", "")
                    if (!ssid.isNullOrBlank() && ssid != "<unknown ssid>") {
                        return ssid
                    }

                    @Suppress("DEPRECATION")
                    val legacySsid = wifiManager?.connectionInfo?.ssid?.replace("\"", "")
                    if (!legacySsid.isNullOrBlank() && legacySsid != "<unknown ssid>") {
                        return legacySsid
                    }

                    return "Wi-Fi (Connected)"
                } else if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    return "Cellular / Mobile Data"
                }
            }

            @Suppress("DEPRECATION")
            val legacySsid = wifiManager?.connectionInfo?.ssid?.replace("\"", "")
            if (!legacySsid.isNullOrBlank() && legacySsid != "<unknown ssid>") {
                return legacySsid
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Wi-Fi SSID", e)
        }
        return "Cellular / Mobile Data"
    }

    private fun getBatteryInfo(): Pair<Int, Boolean> {
        return try {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(null, intentFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(null, intentFilter)
            }
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 100

            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            Pair(pct, isCharging)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching battery status", e)
            Pair(100, false)
        }
    }

    companion object {
        private const val TAG = "TelemetryManager"
    }
}
