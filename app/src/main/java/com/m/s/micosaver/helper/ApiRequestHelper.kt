package com.m.s.micosaver.helper

import android.os.Bundle
import android.util.Log
import com.m.s.micosaver.BuildConfig
import com.m.s.micosaver.Constant
import com.m.s.micosaver.ex.scope
import com.m.s.micosaver.firebase.FirebaseHelper
import com.m.s.micosaver.ms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

object ApiRequestHelper {
    private const val TAG = "ApiRequestHelper"
    private var requestingApi = false

    private val client: OkHttpClient = OkHttpClient.Builder().build()

    fun requestApi(){
        if (!FirebaseHelper.remoteConfig.apiReqSwitch){
            return
        }
        if (ms.data.hasReqApiSuccess){
            return
        }

        if (requestingApi) return
        requestingApi = true

        // 调用api
        scope.launch {
            val head = HashMap<String, String>()
            head.put("SQA", ms.packageName)
            head.put("SQC", BuildConfig.VERSION_NAME)
            var success = false
            var errorMsg = ""
            val result = get(Constant.API_URL, head)
            result.onSuccess { data->
                ms.data.hasReqApiSuccess = true
                success = true
            }.onFailure { error ->
                errorMsg = error.toString()
            }

            requestingApi = false
            if (!success) {
                Log.e(TAG, "request api failed after 3 attempts")
                val bundle = Bundle()
                bundle.putString("msg",errorMsg)
                FirebaseHelper.logEvent("ms_req_api_fail",bundle)
            } else {
                Log.i(TAG, "request api success")
                FirebaseHelper.logEvent("ms_req_api_suc")
            }
        }
    }

    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder().url(url)
            headers.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            val request = requestBuilder.build()
            val response = client.newCall(request).execute()
            response.use {
                if (!response.isSuccessful) {
                    Result.failure(IOException("HTTP ${response.code}"))
                } else {
                    Result.success(response.body?.string() ?: "")
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

}