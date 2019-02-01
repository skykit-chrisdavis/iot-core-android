package com.agosto.clouldiotcore

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import com.agosto.cloudiotcore.DeviceRequestResult
import com.agosto.cloudiotcore.FunctionsApi
import com.google.gson.Gson
import io.reactivex.Observable
import io.reactivex.disposables.Disposable

class IoTCoreViewModel(application: Application): AndroidViewModel(application) {

    val TAG = "IoTCoreViewModel"
    var iotCoreClient: IotCoreClient? = null
    var onConfigSub: Disposable? = null
    var deviceConfig = DeviceConfig()

    fun provision(iotCoreClient: IotCoreClient, functionsApi: FunctionsApi): Observable<Boolean> {
        this.iotCoreClient = iotCoreClient
        return bindIotCoreClientRx().onErrorResumeNext(registerIotDevice(functionsApi))
    }

    fun bindIotCoreClientRx(): Observable<Boolean> {
        return Observable.create {
            try {
                it.onNext(bindIotCoreClient())
                it.onComplete()
            } catch(e: Exception) {
                it.onError(e)
            }
        }
    }

    val isRegistered: Boolean
        get() {
            val projectId = cachedSetting("projectId")
            val registryId = cachedSetting("registryId")
            return (projectId.isNotEmpty() && registryId.isNotEmpty())
        }

    val isProvisioned: Boolean
        get() = iotCoreClient != null

    fun bindIotCoreClient(): Boolean {
        val projectId = cachedSetting("projectId")
        val registryId = cachedSetting("registryId")
        if(projectId.isEmpty() || registryId.isEmpty()) {
            throw Exception("Device not registered in Iot Core")
        }
        this.onConfigSub = iotCoreClient!!.onIotEvent.subscribe {
            when(it) {
                is IotConfig -> {
                    deviceConfig =  Gson().fromJson(it.payload, DeviceConfig::class.java)
                    setLoggingLevel(deviceConfig)
                }
                is IotCommand -> {
                    // todo: refactor this?  maybe remove PlayerMessage and add it to provisioning events
                    val deviceCommand = Gson().fromJson(it.payload, DeviceCommand::class.java)
                    if(deviceCommand.reset) {

                    }
                }
                is IotReconnecting -> {
                    IoTLog.i(TAG,"Reconnecting to Iot Core MQTT")
                }
            }
        }
        return iotCoreClient!!.connect(projectId,registryId)
    }
    
    fun registerIotDevice(functionsApi: FunctionsApi): Observable<Boolean> {
        return functionsApi.registerIotDevice(iotCoreClient!!.buildDeviceRequest())
                .map {
                    cacheIotCoreSetting(it)
                    bindIotCoreClient()
                }
    }

    fun cacheIotCoreSetting(deviceRequestResult: DeviceRequestResult) {
        val pref = getApplication<Application>().getSharedPreferences("demo", Context.MODE_PRIVATE)
        pref.edit {
            putString("projectId",deviceRequestResult.projectId)
            putString("registryId",deviceRequestResult.registryId)
        }
    }

    fun cachedSetting(name:String) : String {
        val pref = getApplication<Application>().getSharedPreferences("demo", Context.MODE_PRIVATE)
        return pref.getString(name,"")?:""
    }
    
    private fun setLoggingLevel(deviceConfig: DeviceConfig) {
        val level = if(deviceConfig.loggingEnabled) deviceConfig.loggingLevel else 0
        IoTLog.d(TAG,"setting logging level to ${deviceConfig.loggingLevel}")
        IoTLog.level = level
        if(deviceConfig.loggingEnabled) {
            IoTLog.startPosting()
        } else {
            IoTLog.stopPosting()
        }
    }

    fun reset() {
        val pref = getApplication<Application>().getSharedPreferences("demo", Context.MODE_PRIVATE)
        pref.edit {
            putString("projectId","")
            putString("registryId","")
        }
        iotCoreClient?.disconnect()
        iotCoreClient = null
    }

    fun publishState(deviceState: DeviceState) : Boolean {
        return iotCoreClient?.publishState(Gson().toJson(deviceState))?: false
    }

    override fun onCleared() {
        super.onCleared()
        IoTLog.d(TAG,"Clearing view model")
        iotCoreClient?.disconnect()
    }
}

class DeviceState(context: Context) {
    val serial = DeviceInfo.serialNumber(context)
    val model = Build.MODEL
    val manufacturer = Build.MANUFACTURER
    val batteryLevel = DeviceInfo.battery(context)
    val memory = DeviceInfo.memory(context)
    val loggingLevel = IoTLog.level
}

class DeviceConfig(val loggingEnabled: Boolean=false, val loggingLevel:Int=0)

class DeviceCommand(val reset: Boolean=false)