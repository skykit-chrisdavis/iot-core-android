package com.agosto.cloudiotcore

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.security.PrivateKey
import java.util.*


object IotCoreMqtt {

    /** Create a Cloud IoT Core JWT for the given project id, signed with the given private key.  */
    @Throws(Exception::class)
    private fun createJwtRsa(projectId: String, privateKey: PrivateKey, timeUnit: Int = Calendar.MINUTE, timeValue: Int=60): String {
        // DateTime now = new DateTime();
        val rightNow = Calendar.getInstance()
        val now = rightNow.time
        rightNow.add(timeUnit, timeValue)
        val exp = rightNow.time
        // Create a JWT to authenticate this device. The device will be disconnected after the token
        // expires, and will have to reconnect with a new token. The audience field should always be set
        // to the GCP project id.

        val jwtBuilder = Jwts.builder()
                .setIssuedAt(now)
                .setExpiration(exp)
                .setAudience(projectId)

        return jwtBuilder.signWith(SignatureAlgorithm.RS256, privateKey).compact()
    }

    /**
     * Connects to IotCore Mqtt and returns a MqttClient
     * @param projectId projectId of your google cloud project
     * @param registryId IoT core registryId
     * @param deviceId deviceId of this device
     * @param privateKey the device generated privateKey
     * @return a connected MqttClient
     * @throws Exception
     */
    @Throws(Exception::class)
    fun connect(projectId: String, registryId: String, deviceId: String, privateKey: PrivateKey, timeUnit: Int = Calendar.MINUTE, timeValue: Int=60): MqttClient {
        val mqttBridgeHostname = "mqtt.googleapis.com"
        val cloudRegion = "us-central1"
        val mqttBridgePort: Short = 8883
        // Build the connection string for Google's Cloud IoT Core MQTT server.
        val mqttServerAddress = String.format("ssl://%s:%s", mqttBridgeHostname, mqttBridgePort)

        // Create our MQTT client. The mqttClientId is a unique string that identifies this device. For
        // Google Cloud IoT Core, it must be in the format below.
        val mqttClientId = String.format("projects/%s/locations/%s/registries/%s/devices/%s", projectId, cloudRegion, registryId, deviceId)

        val connectOptions = MqttConnectOptions()
        // Note that the the Google Cloud IoT Core only supports MQTT 3.1.1, and Paho requires that we
        // explictly set this. If you don't set MQTT version, the server will immediately close its
        // connection to your device.
        connectOptions.mqttVersion = MqttConnectOptions.MQTT_VERSION_3_1_1

        // With Google Cloud IoT Core, the username field is ignored, however it must be set for the
        // Paho client library to send the password field. The password field is used to transmit a JWT
        // to authorize the device.
        connectOptions.userName = "unused"

        connectOptions.password = createJwtRsa(projectId, privateKey).toCharArray()

        // Create a client, and connect to the Google MQTT bridge.
        val client = MqttClient(mqttServerAddress, mqttClientId, MemoryPersistence())
        client.connect(connectOptions)
        return client
    }

    /**
     * The MQTT topic that this device will publish telemetry data to. The MQTT topic name is
     * required to be in the format below. Note that this is not the same as the device registry's
     * Cloud Pub/Sub topic.
     * @param deviceId id of device in registry
     * @return topic string
     */
    fun telemetryTopic(deviceId: String): String {
        return "/devices/$deviceId/events"
    }

    fun telemetryTopic(deviceId: String, subTopic: String): String {
        return "/devices/$deviceId/events/$subTopic"
    }

    /**
     * device config topic to receive device config settings
     * @param deviceId id of device in registry
     * @return topic string
     */
    fun configTopic(deviceId: String): String {
        return "/devices/$deviceId/config"
    }

    /**
     * device state topic to publish state data
     * @param deviceId id of device in registry
     * @return topic string
     */
    fun stateTopic(deviceId: String): String {
        return "/devices/$deviceId/state"
    }

    fun commandTopic(deviceId: String): String {
        return "/devices/$deviceId/commands/#"
    }

}
