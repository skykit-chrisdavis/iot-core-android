package com.agosto.clouldiotcore

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.isGone
import com.agosto.cloudiotcore.DeviceKeys
import com.agosto.cloudiotcore.DeviceRequest
import com.agosto.cloudiotcore.FunctionsApi
import com.agosto.cloudiotcore.IotCoreMqtt
import com.github.florent37.runtimepermission.kotlin.askPermission
import com.google.gson.Gson
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.activity_main.*
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    lateinit var deviceKeys: DeviceKeys
    var deviceId = ""
    var projectId=""
    var registryId = ""
    var accessCode = ""
    var mqttClient: MqttClient? = null
    var publishHandler = Handler()
    var publishIntervalMs = 20000L
    var lastPublishDate = Date()
    var publishCount= 0;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        deviceKeys = DeviceKeys(this)
        val cert  = "-----BEGIN CERTIFICATE-----\n" + deviceKeys.encodedCertificate() + "-----END CERTIFICATE-----\n"
        deviceId = ""
        val pref = PreferenceManager.getDefaultSharedPreferences(this);
        projectId = pref.getString("projectId","")?:""
        registryId = pref.getString("registryId","")?:""
        registerDevice.setOnClickListener(this::registerIotDevice)
        Log.d(TAG,cert)
    }

    override fun onStart() {
        super.onStart()
        checkForSerialPermissions()
    }

    override fun onStop() {
        publishHandler.removeCallbacksAndMessages(null)
        disconnectTotCore()
        //publishHandler.removeCallbacks(null)
        super.onStop()
    }

    fun checkIotCore() {
        deviceId = "device-${DeviceInfo.serialNumber(this)}"
        if(projectId.isEmpty()) {
            accessCodeEdit.setText("")
            registeredView.isGone = true
            accessView.isGone = false
        } else {
            showRegisteredView()
            connectIotCore()
        }
    }

    fun showRegisteredView() {
        updateDeviceDetails()
        accessView.isGone = true
        registeredView.isGone = false
    }

    fun updateDeviceDetails() {
        deviceName.text = "${Build.MANUFACTURER} ${Build.MODEL}"
        deviceSerial.text = DeviceInfo.serialNumber(this)
        deviceDetailId.text = deviceId
    }

    fun updateIotCoreDetails() {
        publishedRate.text = "${publishIntervalMs}ms"
        statePublishedCount.text = "State $publishCount"
        telemetryPublishedCount.text = "Telemetry $publishCount"
        iotCoreLastPublished.text = getISO8601StringForDate(lastPublishDate)
    }

    fun checkForSerialPermissions() {
        askPermission(Manifest.permission.READ_PHONE_STATE){
            checkIotCore()
        }.onDeclined { e ->
            if (e.hasDenied()) {
                AlertDialog.Builder(this)
                    .setMessage(R.string.premission_reason)
                    .setPositiveButton("yes") { dialog, which ->
                        //e.askAgain()
                        checkForSerialPermissions()
                    } //ask again
                    .setNegativeButton("no") { dialog, which ->
                        dialog.dismiss()
                        checkIotCore()
                        // start
                    }
                    .show();
            }

            if(e.hasForeverDenied()) {
                //start
                checkIotCore()
            }
        }
    }

    fun registerIotDevice(view: View) {
        hideKeyboard()
        if(accessCodeEdit.text.isEmpty()) {
            Toast.makeText(this,"Must enter code", Toast.LENGTH_SHORT).show()
            return
        }
        val token = accessCodeEdit.text.toString()
        val cert  = "-----BEGIN CERTIFICATE-----\n" + deviceKeys.encodedCertificate() + "-----END CERTIFICATE-----\n"
        val functionsApi =  FunctionsApi.create(CLOUD_FUNCTIONS_URL,token)
        val sub = functionsApi.registerIotDevice(DeviceRequest(deviceId,cert))
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe( {
                accessCode = token
                projectId = it.projectId
                registryId = it.registryId
                saveSettings()
                showRegisteredView()
            },{
                Log.w(TAG,it)
                Toast.makeText(this,"Error: ${it}", Toast.LENGTH_SHORT).show()
            })
    }

    fun saveSettings() {
        val pref = PreferenceManager.getDefaultSharedPreferences(this);
        pref.edit {
            putString("projectId",projectId)
            putString("registryId",registryId)
        }
    }

    fun hideKeyboard() {
        val view = findViewById<View>(android.R.id.content)
        if (view != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    protected fun connectIotCore() {
        try {
            Log.d(TAG,"Connecting to IoT Core MQTT")
            mqttClient = IotCoreMqtt.connect(projectId, registryId, deviceId, deviceKeys.privateKey!!)
            Log.d(TAG,"Subscribing config topic")
            mqttClient?.subscribe(IotCoreMqtt.configTopic(deviceId)) { topic, message ->
                val json = String(message.payload)
                Log.d(TAG,"Device Config: $json")
            }
            startPublishing(1000)
        } catch (e: Exception) {
            e.printStackTrace()
            publishHandler.postDelayed({
                connectIotCore()
            },5000)
        }

    }

    protected fun disconnectTotCore() {
        if (mqttClient != null) {
            publishHandler.removeCallbacks(null)
            try {
                mqttClient?.disconnect()
            } catch (e: MqttException) {
                e.printStackTrace()
            }
        }
    }

    protected fun startPublishing(delayMs: Long) {
        publishHandler.postDelayed( {
            try {
                publishTelemetry()
                publishState()
                publishCount++
                startPublishing(publishIntervalMs)
                updateIotCoreDetails()
                updateDeviceDetails()
            } catch (e: MqttException) {
                Log.w(TAG, e.toString())
                Log.d(TAG,"reconnecting...")
                connectIotCore()
            }
        }, delayMs)
    }

    fun publishState() {
        try {
            val publishDate = Date()
            val deviceState = DeviceState(this)
            val payload = Gson().toJson(deviceState)
            Log.d(TAG, "Publishing state message: $payload")
            val message = MqttMessage(payload.toByteArray())
            message.qos = 1
            mqttClient?.publish(IotCoreMqtt.stateTopic(deviceId), message)
            lastPublishDate = publishDate
        } catch (e: MqttException) {
            throw e
        }
    }

    fun publishTelemetry() {
        try {
            val payload = "$deviceId ${getISO8601StringForDate()}"
            Log.d(TAG, "Publishing telemetry message: $payload")
            val message = MqttMessage(payload.toByteArray())
            message.qos = 1
            mqttClient?.publish(IotCoreMqtt.telemetryTopic(deviceId), message)

        } catch (e: MqttException) {
            throw e
        }
    }


    companion object {
        const val TAG = "MainActivity"
        const val CLOUD_FUNCTIONS_URL = "https://us-central1-${BuildConfig.FIREBASE_PROJECT_ID}.cloudfunctions.net/"

        fun getISO8601StringForDate(date: Date = Date()): String {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            dateFormat.timeZone = TimeZone.getTimeZone("UTC")
            return dateFormat.format(date)
        }
    }
}
