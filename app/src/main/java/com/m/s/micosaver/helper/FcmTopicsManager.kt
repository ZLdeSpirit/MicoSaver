package com.m.s.micosaver.helper

import android.util.Base64
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.m.s.micosaver.Constant
import com.m.s.micosaver.ex.FCMTopicExtensions.subscribeTopic
import com.m.s.micosaver.ex.FCMTopicExtensions.subscribeTopics
import com.m.s.micosaver.ex.scope
import com.m.s.micosaver.firebase.FirebaseHelper
import kotlinx.coroutines.launch
import org.json.JSONArray

object FcmTopicsManager {
    private const val TAG = "FcmTopicsManager"
    private val defaultTopics = Constant.TOPIC

    fun appStartRegisterTopics(){
        registerDefaultTopics()
        remoteFcmTopicsRegister()
    }

    fun registerDefaultTopics(){
        scope.launch {
            FirebaseMessaging.getInstance().subscribeTopics(defaultTopics)
        }
    }

    fun remoteFcmTopicsRegister() {
        val config = FirebaseHelper.remoteConfig.getFcmTopics()
        Log.i(TAG, "remoteFcmTopicsRegister config: $config")
        if (config.isEmpty()) return
        runCatching {
            scope.launch {
                val jsonArray = JSONArray(String(Base64.decode(config, Base64.NO_WRAP)))
                for (index in 0 until jsonArray.length()) {
                    val topic = jsonArray.getString(index)
                    FirebaseMessaging.getInstance().subscribeTopic(topic)
                }
            }

        }
    }
}