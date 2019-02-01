package com.agosto.clouldiotcore

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.lifecycle.ViewModelProviders
import com.agosto.cloudiotcore.DeviceKeys
import com.agosto.cloudiotcore.FunctionsApi
import com.github.florent37.runtimepermission.kotlin.askPermission
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_main.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    lateinit var deviceKeys: DeviceKeys
    lateinit var ioTCoreViewModel: IoTCoreViewModel
    var deviceId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        deviceKeys = DeviceKeys(this)
        ioTCoreViewModel= ViewModelProviders.of(this).get(IoTCoreViewModel::class.java)
        ioTCoreViewModel.onEvent
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {event->onDeviceEvent(event)},
                {error->IoTLog.w(TAG,"IoT Event error: $error")})

        registerDevice.setOnClickListener(this::registerIotDevice)
        IoTLog.i(TAG,"onCreate")
        warningLog.setOnClickListener {
            IoTLog.w(TAG,"This an Warn log at ${getISO8601StringForDate()}")
        }
        infoLog.setOnClickListener {
            IoTLog.i(TAG,"This an Info log at ${getISO8601StringForDate()}")
        }
        debugLog.setOnClickListener {
            IoTLog.d(TAG,"This an Debug log at ${getISO8601StringForDate()}")
        }
        verboseLog.setOnClickListener {
            IoTLog.v(TAG,"This an Verbose log at ${getISO8601StringForDate()}")
        }
        deviceState.setOnClickListener {
            ioTCoreViewModel.publishState(DeviceState(this))
        }
    }

    private fun onDeviceEvent(event: DeviceEvent) {
        when(event) {
            is ConfigUpdate -> {
                updateDeviceDetails(event.deviceConfig)
            }
            is DeviceDelete -> {
                checkIotCore()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        IoTLog.i(TAG,"onStart")
        checkForSerialPermissions()
    }

    override fun onStop() {
        IoTLog.i(TAG,"onStop")
        super.onStop()
    }

    fun checkIotCore() {
        IoTLog.i(TAG,"checking ito core state")
        updateDeviceDetails()
        if(!ioTCoreViewModel.isRegistered) {
            accessCodeEdit.setText("")
            registeredView.isGone = true
            accessView.isGone = false
        } else if (!ioTCoreViewModel.isProvisioned) {
            provisionDevice()
        } else {
            showRegisteredView()
        }
    }

    fun provisionDevice(token: String=""): Disposable? {
        deviceId = "${DeviceInfo.devicePrefix()}-${DeviceInfo.serialNumber(this)}"
        val iotCoreClient = IotCoreClient(deviceId,deviceKeys)
        val functionsApi =  FunctionsApi.create(CLOUD_FUNCTIONS_URL,token)
        IoTLog.i(TAG,"provisioning ${deviceId}")
        IoTLog.labels["serialNumber"] = DeviceInfo.serialNumber(this)
        return ioTCoreViewModel.provision(iotCoreClient,functionsApi)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                IoTLog.iotCoreClient = iotCoreClient
                IoTLog.i(TAG,"Player provisioned, connected=$it")
                showRegisteredView()
            }, {
                IoTLog.w(TAG,"Fatal error provisioning player $it")
            })
    }

    fun showRegisteredView() {
        updateDeviceDetails()
        accessView.isGone = true
        registeredView.isGone = false
    }

    fun updateDeviceDetails(deviceConfig: DeviceConfig = DeviceConfig()) {
        deviceName.text = "${Build.MANUFACTURER} ${Build.MODEL}"
        deviceSerial.text = DeviceInfo.serialNumber(this)
        deviceDetailId.text = deviceId
        loggingDetails.text = deviceConfig.detailsText()
    }

    fun checkForSerialPermissions() {
        askPermission(Manifest.permission.READ_PHONE_STATE){
            checkIotCore()
        }.onDeclined { e ->
            if (e.hasDenied()) {
                AlertDialog.Builder(this)
                    .setMessage(R.string.permission_reason)
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
        IoTLog.i(TAG,"registering new device")
        provisionDevice(accessCodeEdit.text.toString())
    }

    fun hideKeyboard() {
        val view = findViewById<View>(android.R.id.content)
        if (view != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
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
