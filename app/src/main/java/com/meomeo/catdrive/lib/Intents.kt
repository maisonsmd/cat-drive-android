package com.meomeo.catdrive.lib

import com.meomeo.catdrive.BuildConfig

class Intents {
    companion object {
        private const val appId = BuildConfig.APPLICATION_ID

        const val EnableServices = "${appId}.intent.ENABLE_SERVICES"
        const val DisableServices = "${appId}.intent.DISABLE_SERVICES"
        const val BindLocalService = "${appId}.intent.LOCAL_BIND"

        const val NavigationUpdate = "${appId}.intent.NAVIGATION_UPDATE"
        const val GpsUpdate = "${appId}.intent.GPS_UPDATE"
    }
}