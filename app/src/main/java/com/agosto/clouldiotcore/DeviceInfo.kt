package com.agosto.clouldiotcore

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.preference.PreferenceManager
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import java.util.*


object DeviceInfo {

    var cachedSerialNumber = ""

	fun serialNumber(context: Context): String {
		val serial = hardSerial(context)
        cachedSerialNumber = serial
		if(serial.isNotEmpty() && serial != "unknown") {
			return serial
		}
		return softSerial(context)
	}

    fun hardSerial(context: Context): String {
        var serial = ""
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            serial = Build.SERIAL
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            serial = Build.getSerial()
        }
        return serial
    }

	fun softSerial(context: Context): String {
        val pref = PreferenceManager.getDefaultSharedPreferences(context);
		var serial = pref.getString("serialNumber","")?:""
		if(serial.isEmpty()) {
			serial = "SS${deviceNum()}"
			pref.edit {
                putString("serialNumber",serial)
            }
		}
        cachedSerialNumber = serial
        return serial
	}

	fun deviceNum(): String {
		return UUID.randomUUID().toString().substring(0, 8).toUpperCase()
	}

	fun battery(context: Context): Int {
		val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
		return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
	}

    fun memory(context: Context): Long {
        val memoryInfo = ActivityManager.MemoryInfo()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE)
        (activityManager as ActivityManager).getMemoryInfo(memoryInfo)
        return memoryInfo.availMem
    }

    fun devicePrefix(): String {
        return if (Build.MODEL.isEmpty()) {
            "Device"
        } else {
            val model = Build.MODEL.replace(" ","").toLowerCase()
            val re = Regex("[^A-Za-z0-9 ]")
            val pre = re.replace(model, "")
            if(pre[0].isLetter()) {
                pre
            } else {
                "Device-$pre"
            }
        }
    }
}