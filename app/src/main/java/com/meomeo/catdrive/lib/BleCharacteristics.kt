package com.meomeo.catdrive.lib

class BleCharacteristics {
    companion object {
        const val SERVICE_SETTINGS = "ec91d7ab-e87c-48d5-adfa-cc4b2951298a"
        const val CHA_SETT_THEME = "9d37a346-63d3-4df6-8eee-f0242949f59f"
        const val CHA_SETT_BRIGHTNESS = "0b11deef-1563-447f-aece-d3dfeb1c1f20"
        const val CHA_SETT_SPEED_LIMIT = "d4d8fcca-16b2-4b8e-8ed5-90137c44a8ad"

        const val SERVICE_NAV = "55a919e3-73b6-4706-ac09-e8dcaf738a96"
        const val CHA_NAV_SPEED = "f2e3336e-640b-4d4c-b0df-5bbacb7cfd22"
        const val CHA_NAV_NEXT_ROAD = "de85b367-aaec-493b-b317-6f91f8eae852"
        const val CHA_NAV_NEXT_ROAD_DESC = "4b115f28-fd93-4113-b3b1-b09f14d46045"
        const val CHA_NAV_DISTANCE_TO_NEXT_TURN = "2fe1df0f-5ea0-45e1-a5fd-43b3f9768188"
        const val CHA_NAV_ETA = "245be16d-e9c4-4b54-bdd8-2966704b727c"
        const val CHA_NAV_ETE = "a4f3b12d-dda0-42d7-9f2f-dad257d81fb2"
        const val CHA_NAV_TOTAL_DISTANCE = "c685be4d-31d2-4f27-b4be-08d6e2c1c6dc"
        const val CHA_NAV_TBT_ICON = "9931ab38-bb10-49ec-aec9-0c2cee5f3416"
    }
}