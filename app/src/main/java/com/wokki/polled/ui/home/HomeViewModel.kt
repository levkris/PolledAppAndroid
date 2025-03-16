package com.wokki.polled.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.json.JSONObject

class HomeViewModel : ViewModel() {

    // LiveData to hold cached timeline data
    private val _timelineData = MutableLiveData<List<JSONObject>>()
    val timelineData: LiveData<List<JSONObject>> get() = _timelineData

    // Method to update the timeline data
    fun setTimelineData(timeline: List<JSONObject>) {
        _timelineData.value = timeline
    }
}
