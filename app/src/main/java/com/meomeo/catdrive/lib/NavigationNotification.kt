package com.meomeo.catdrive.lib

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification

open class NavigationNotification(context: Context, statusBarNotification: StatusBarNotification) {
    protected val mNotification: Notification = statusBarNotification.notification
    protected val mContext = context
    protected var mAppSourceContext: Context = mContext.createPackageContext(
        statusBarNotification.packageName,
        Context.CONTEXT_IGNORE_SECURITY
    )

    var mNavigationData: NavigationData = NavigationData()
        private set

    init {
        mNavigationData.postTime = NavigationTimestamp(statusBarNotification.postTime)
    }
}
