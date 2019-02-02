# Iot Core Android

Library and example app for the **IoT Core for Android Developers** MN DevFest presentation.  
https://devfest.mn/schedule/-LVakWwV25Wl2ynVUbXi/Cloud-IoT-Core-for-Android-Developers

This project has a companion firebase project which implements cloud functions that facilities device registration and receives the data published on the device state and telemetry topics.  
https://github.com/agosto-chrisdavis/iot-core-firebase

## Setup

The example needs a `config.json` file that sets the firebase project where the cloud functions are deployed.  It should like like this:

```json
{
  "projectId":"agosto-iot-core-demo"
}
```

Place the `config.json` in the `app` directory.  

## Cloud IoT Core

https://cloud.google.com/iot-core/