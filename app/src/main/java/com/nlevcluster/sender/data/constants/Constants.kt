package com.nlevcluster.sender.data.constants

object Constants {
    const val PACKAGE_NAME = "PACKAGE_NAME"
    const val SUBSCRIBE_PROCESSOR_TOPIC = "processor/cloud"

    //    Broadcast Actions
    const val ACTION_LOGS_COLLECTED = "com.nlev.ACTION_LOGS_COLLECTED"
    const val ACTION_OTA_UPDATE_AVAILABLE = "com.nlev.ACTION_OTA_UPDATE_AVAILABLE"
    const val OTA_DATA = "OTA_DATA"


    const val DEFAULT_IGN_ON_DELAY = 15000
    const val DEFAULT_IGN_OFF_DELAY = 60000
    const val APPLY_VIP_URI = "content://com.nlevcluster.nl_admin.vip_provider/apply_vip"
    const val MODEL_VIP_URI = "content://com.nlevcluster.nl_admin.vip_provider/model_vip"

    // Portal MQTT Topics
    const val TOPIC_RAW_FEED = "RAW_FEED"
    const val TOPIC_HEALTH_PACKET = "HEALTH_PACKET"
    const val TOPIC_TRIP_REPORT = "TRIP_REPORT"
    const val MQTT_PORTAL_URL = "evcluster.nesh.live"
    const val MQTT_PORTAL_PORT = 1883
    const val TEST_IMEI = "911233851860432"
}