package com.agosto.clouldiotcore

import com.agosto.cloudiotcore.DeviceKeys
import com.agosto.cloudiotcore.DeviceRequest
import com.agosto.cloudiotcore.IotCoreMqtt
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.ObservableOnSubscribe
import io.reactivex.disposables.Disposable
import org.eclipse.paho.client.mqttv3.*
import java.util.concurrent.TimeUnit

sealed class IotCoreEvent
data class IotConfig(val payload: String) : IotCoreEvent()
data class IotCommand(val payload: String) : IotCoreEvent()
object IotReconnecting : IotCoreEvent()

class IotCoreClient (val deviceId: String, val deviceKeys: DeviceKeys) : MqttCallback {

    var projectId=""
    var registryId =""
    var mqttClient: MqttClient? = null
    var reconnectTimeout = RECONNECT_TIMEOUT

    lateinit var eventEmitter: ObservableEmitter<IotCoreEvent>
    val onIotEvent = Observable.create(ObservableOnSubscribe<IotCoreEvent> {
        eventEmitter = it
    }).publish()

    init {
        onIotEvent.connect()
    }

    var failedConnects = 0
    var maxReconnectRetries = 0 // 0 is always reconnect

    //val connectionHandler = Handler()
    var disposable: Disposable? = null

    fun connect(projectId: String, registryId: String): Boolean {
        this.registryId = registryId
        this.projectId = projectId
        try {
            IoTLog.d(TAG,"Connecting to IoT Core MQTT")
            mqttClient = IotCoreMqtt.connect(projectId, registryId, deviceId, deviceKeys.privateKey!!)
            if(mqttClient!=null) {
                subscribe()
            }
            failedConnects = 0
            return true
        } catch (e: Exception) {
            //e.printStackTrace()
            IoTLog.w(TAG,"Error connecting to Iot Core $e, retrying in $reconnectTimeout ms")
            /*disposable = Observable.timer(reconnectTimeout, TimeUnit.MILLISECONDS).subscribe {
                connect()
            }*/
            failedConnects++
            connect(reconnectTimeout)
            return false
        }
    }

    private fun connect(delay: Long) {
        disconnect()
        if(shouldReconnect()) {
            disposable = Observable.timer(delay, TimeUnit.MILLISECONDS).subscribe {
                if (connect(projectId, registryId)) {
                    IoTLog.i(TAG, "delayed MQTT connection successful")
                } else {
                    IoTLog.w(TAG, "delayed MQTT connection failed $failedConnects times")
                }
            }
            eventEmitter.onNext(IotReconnecting)
        }
    }

    private fun shouldReconnect() : Boolean {
        if(maxReconnectRetries == 0) {
            return true
        }
        return (failedConnects < maxReconnectRetries)
    }

    fun disconnect() {
        disposable?.dispose()
        if (mqttClient != null) {
            IoTLog.i(TAG,"Disconnecting from Iot Core")
            try {
                mqttClient?.setCallback(null)
                mqttClient?.disconnect()
                mqttClient = null
            } catch (e: MqttException) {
                IoTLog.w(TAG,"Error disconnecting form Iot Core $e")
            }
        }
    }

    fun subscribe() {
        IoTLog.d(TAG,"Subscribing config and command topics")
        mqttClient?.subscribe(IotCoreMqtt.configTopic(deviceId))
        mqttClient?.subscribe(IotCoreMqtt.commandTopic(deviceId))
        mqttClient?.setCallback(this)
    }

    override fun messageArrived(topic: String?, message: MqttMessage?) {
        IoTLog.d(TAG,"MQTT message for topic $topic")
        val configTopic = IotCoreMqtt.configTopic(deviceId)
        val commandTopic = IotCoreMqtt.commandTopic(deviceId)
        if(message!=null && topic!=null) {
            val json = String(message.payload)
            IoTLog.d(TAG, "MQTT mesage payload: $json")
            if (topic == configTopic) {
                eventEmitter.onNext(IotConfig(json))
            } else if (commandTopic.startsWith(topic)) {
                eventEmitter.onNext(IotCommand(json))
            } else {
                IoTLog.w(TAG, "Message from unknown topic $topic")
            }
        }
    }

    override fun connectionLost(cause: Throwable?) {
        IoTLog.i(TAG,"connectionLost $cause")
        connect(reconnectTimeout)
    }

    override fun deliveryComplete(token: IMqttDeliveryToken?) {
        //IoTLog.i(TAG,"deliveryComplete $token")
    }

    fun publishState(payload: String): Boolean {
        return try {
            IoTLog.d(TAG, "Publishing state message: $payload")
            val message = MqttMessage(payload.toByteArray())
            message.qos = 1
            mqttClient?.publish(IotCoreMqtt.stateTopic(deviceId), message)
            true
        } catch (e: MqttException) {
            IoTLog.w(TAG,"Mqtt publish state error: $e")
            //connect()
            false
        }
    }

    fun publishTelemetry(payload: ByteArray, subTopic: String=""): Boolean {
        return try {
            val topic = if(subTopic.isEmpty()) IotCoreMqtt.telemetryTopic(deviceId) else IotCoreMqtt.telemetryTopic(deviceId,subTopic)
            IoTLog.d(TAG, "Publishing ${payload.size} bytes to telemetry topic $topic")
            val message = MqttMessage(payload)
            message.qos = 1
            mqttClient?.publish(topic, message)
            true
        } catch (e: MqttException) {
            IoTLog.w(TAG,"Mqtt publish telemetry error: $e")
            //connect()
            false
        }
    }

    fun publishTelemetry(payload: String, subTopic: String=""): Boolean {
        return publishTelemetry(payload.toByteArray(),subTopic)
    }

    fun buildDeviceRequest(): DeviceRequest {
        val cert  = "-----BEGIN CERTIFICATE-----\n" + deviceKeys.encodedCertificate() + "-----END CERTIFICATE-----\n"
        return DeviceRequest(deviceId,cert)
    }

    companion object {
        const val TAG = "IotCoreClient"
        const val RECONNECT_TIMEOUT = 5000L
        const val MAX_PUB_SIZE_BYTES = 200000
    }
}