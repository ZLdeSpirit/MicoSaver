package com.m.s.micosaver.ex

import com.m.s.micosaver.firebase.FirebaseHelper
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

/**
 * Firebase Messaging 主题订阅的协程扩展函数
 * 所有函数都是挂起函数，需要在协程作用域中调用
 */
object FCMTopicExtensions {

    private const val TAG = "FCMTopicExt"

    /**
     * 订阅主题 - 基础版本
     * 返回 Result<Unit>，成功或失败都有明确的结果
     *
     * 使用示例：
     * ```kotlin
     * viewModelScope.launch {
     *     val result = FirebaseMessaging.getInstance().subscribeTopic("news")
     *     result.onSuccess {
     *         // 订阅成功
     *     }.onFailure { e ->
     *         // 订阅失败
     *     }
     * }
     * ```
     */
    suspend fun FirebaseMessaging.subscribeTopic(topic: String): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                subscribeToTopic(topic).await()
            }
            Log.i(TAG, "✅ 订阅成功: $topic")
            FirebaseHelper.logEvent("topic_sub_succ_$topic")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 订阅失败: $topic", e)
            FirebaseHelper.logEvent("topic_sub_fail_$topic")
            Result.failure(e)
        }
    }

    /**
     * 取消订阅主题 - 基础版本
     * 返回 Result<Unit>
     *
     * 使用示例：
     * ```kotlin
     * viewModelScope.launch {
     *     val result = FirebaseMessaging.getInstance().unsubscribeTopic("news")
     *     result.onSuccess {
     *         // 取消订阅成功
     *     }.onFailure { e ->
     *         // 取消订阅失败
     *     }
     * }
     * ```
     */
    suspend fun FirebaseMessaging.unsubscribeTopic(topic: String): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                unsubscribeFromTopic(topic).await()
            }
            Log.i(TAG, "✅ 取消订阅成功: $topic")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 取消订阅失败: $topic", e)
            Result.failure(e)
        }
    }

    /**
     * 订阅主题 - 返回 Boolean 版本
     * 简洁版，只返回是否成功
     *
     * 使用示例：
     * ```kotlin
     * viewModelScope.launch {
     *     val success = FirebaseMessaging.getInstance().subscribeTopicBoolean("news")
     *     if (success) {
     *         // 订阅成功
     *     }
     * }
     * ```
     */
    suspend fun FirebaseMessaging.subscribeTopicBoolean(topic: String): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                subscribeToTopic(topic).await()
            }
            Log.i(TAG, "✅ 订阅成功: $topic")
            FirebaseHelper.logEvent("topic_sub_succ_$topic")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ 订阅失败: $topic", e)
            FirebaseHelper.logEvent("topic_sub_fail_$topic")
            false
        }
    }

    /**
     * 取消订阅主题 - 返回 Boolean 版本
     *
     * 使用示例：
     * ```kotlin
     * viewModelScope.launch {
     *     val success = FirebaseMessaging.getInstance().unsubscribeTopicBoolean("news")
     *     if (success) {
     *         // 取消订阅成功
     *     }
     * }
     * ```
     */
    suspend fun FirebaseMessaging.unsubscribeTopicBoolean(topic: String): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                unsubscribeFromTopic(topic).await()
            }
            Log.i(TAG, "✅ 取消订阅成功: $topic")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ 取消订阅失败: $topic", e)
            false
        }
    }

    /**
     * 批量订阅主题
     * 返回每个主题的订阅结果
     *
     * 使用示例：
     * ```kotlin
     * viewModelScope.launch {
     *     val results = FirebaseMessaging.getInstance().subscribeTopics(
     *         listOf("news", "sports", "tech")
     *     )
     *     results.forEach { (topic, success) ->
     *         println("$topic: ${if (success) "成功" else "失败"}")
     *     }
     * }
     * ```
     */
    suspend fun FirebaseMessaging.subscribeTopics(topics: List<String>): Map<String, Boolean> {
        val results = mutableMapOf<String, Boolean>()
        topics.forEach { topic ->
            results[topic] = subscribeTopicBoolean(topic)
        }
        return results
    }

    /**
     * 批量取消订阅主题
     */
    suspend fun FirebaseMessaging.unsubscribeTopics(topics: List<String>): Map<String, Boolean> {
        val results = mutableMapOf<String, Boolean>()
        topics.forEach { topic ->
            results[topic] = unsubscribeTopicBoolean(topic)
        }
        return results
    }
}