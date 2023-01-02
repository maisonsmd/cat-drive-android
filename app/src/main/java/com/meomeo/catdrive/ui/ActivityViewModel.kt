package com.meomeo.catdrive.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ActivityViewModel : ViewModel() {
    private val mPermissionUpdatedTimestamp = MutableLiveData<Long>().apply {
        value = 0
    }
    var permissionUpdatedTimestamp: MutableLiveData<Long>
        get() = mPermissionUpdatedTimestamp
        set(value) = mPermissionUpdatedTimestamp.postValue(value.value)
}