package com.meomeo.catdrive.lib

import android.graphics.Bitmap
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class NavigationDirection(
    val nextRoad: String? = null,
    val nextRoadAdditionalInfo: String? = null,
    val distance: String? = null,
) : Parcelable

@Parcelize
@Serializable
data class NavigationEta(
    val eta: String? = null,
    val ete: String? = null,
    val distance: String? = null
) : Parcelable

@Parcelize
@Serializable
data class NavigationIcon(
    @Serializable(with = BitmapSerializer::class)
    val bitmap: Bitmap? = null,
) : Parcelable, AutoCloseable {
    override fun equals(other: Any?): Boolean {
        return if (other !is NavigationIcon || bitmap !is Bitmap)
            super.equals(other)
        else bitmap.sameAs(other.bitmap)
    }

    override fun close() {
        bitmap?.recycle()
    }
}

@Parcelize
@Serializable
data class NavigationTimestamp(
    @Mutable
    var timestamp: Long = 0,
) : Parcelable, MutableContent() {
    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }
}

@Parcelize
@Serializable
data class NavigationData(
    var nextDirection: NavigationDirection = NavigationDirection(),
    var eta: NavigationEta = NavigationEta(),
    var actionIcon: NavigationIcon = NavigationIcon(),
    @Mutable
    var postTime: NavigationTimestamp = NavigationTimestamp(),
) : Parcelable, Introspectable, MutableContent() {
    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }
}