package com.m.s.micosaver.helper

import android.util.Log
import androidx.core.os.bundleOf
import com.m.s.micosaver.Constant
import com.m.s.micosaver.db.RecommendDataBase
import com.m.s.micosaver.db.info.RecommendBean
import com.m.s.micosaver.ex.scope
import com.m.s.micosaver.firebase.FirebaseHelper
import com.m.s.micosaver.ms
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

object RecommendManager {
    private const val TAG = "RecommendManager"

    private val dao
        get() = RecommendDataBase.database.recommendDao()

    fun getPurchaseUserFunList(callback: (ArrayList<RecommendBean>) -> Unit) {
        if (System.currentTimeMillis() - ms.data.getRecommendRequestTime() >= 1800 * 1000) {
            // 需要请求recommend api
            Log.i(TAG, "get recommend from api")
            scope.launch {
                val completableDeferred = CompletableDeferred<ArrayList<RecommendBean>?>()
                requestRecommend(completableDeferred)
                withContext(Dispatchers.Main) {
                    val list = completableDeferred.await()
                    if (list == null) {
                        callback(getPurchaseUserDefaultFunList())
                    } else {
                        callback(list)
                    }
                }
            }

        } else {
            Log.i(TAG, "get recommend from db")
            scope.launch {
                try {
                    val list = queryRecommend()
                    withContext(Dispatchers.Main) {
                        val recommendList = ArrayList<RecommendBean>()
                        recommendList.addAll(list)
                        callback(recommendList)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        callback(getPurchaseUserDefaultFunList())
                    }
                }

            }
        }
    }

    fun queryRecommend(): List<RecommendBean> {
        return dao.queryRecommend()
    }

    suspend fun replaceAllRecommends(recommends: List<RecommendBean>) {
        dao.replaceAllRecommends(recommends)
    }

    private suspend fun requestRecommend(defer: CompletableDeferred<ArrayList<RecommendBean>?>) {
        try {
            val result = requestRecommend(
                Constant.BASE_RECOMMEND_URL, hashMapOf(
                    "SQA" to ms.packageName
                )
            )
            result.onSuccess { response ->
                requestSuccess(response, defer)
            }.onFailure { error ->
                Log.i(TAG, "recommend_req_fail: is not success")
                FirebaseHelper.logEvent("recommend_req_fail")
                defer.complete(null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.i(TAG, "recommend_req_happen_exce: ${e.message}")
            FirebaseHelper.logEvent("recommend_req_happen_exce")
            defer.complete(null)
        }
    }

    private suspend fun requestRecommend(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder().url(url)
            headers.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            val request = requestBuilder.build()
            val response = configOkHttpClientBuilder().build().newCall(request).execute()
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

    private fun configOkHttpClientBuilder(): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder()
        try {
            val trustManager = object : X509TrustManager {
                override fun checkClientTrusted(
                    chain: Array<out X509Certificate>?, authType: String?
                ) = Unit

                override fun checkServerTrusted(
                    chain: Array<out X509Certificate>?, authType: String?
                ) = Unit

                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()

            }
            val sslContext = SSLContext.getInstance("SSL")
            val array = arrayOf(trustManager)
            val random = SecureRandom()
            sslContext.init(null, array, random)
            builder.sslSocketFactory(sslContext.socketFactory, trustManager)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        builder.hostnameVerifier { _, _ -> true }
        return builder
    }

    private suspend fun requestSuccess(body: String?, defer: CompletableDeferred<ArrayList<RecommendBean>?>) {
        Log.i(TAG, "recommend_req_succ")
        FirebaseHelper.logEvent("recommend_req_succ")

        if (body.isNullOrEmpty()) {
            Log.i(TAG, "recommend_req_fail: body is null")
            FirebaseHelper.logEvent("recommend_req_fail_body_null")
            defer.complete(null)
            return
        }
        Log.i(TAG, "parse body: $body")
        val jsonObject = JSONObject(body)
        val code = jsonObject.getInt("code")
        if (code == 0) {
            val dataArray = jsonObject.getJSONArray("data")
            if (dataArray.length() > 0) {
                val recommendList = parseRecommend(dataArray)
                ms.data.setRecommendRequestTime(System.currentTimeMillis())
                replaceAllRecommends(recommendList)
                defer.complete(recommendList)
            } else {
                FirebaseHelper.logEvent("recom_parse_data_empty")
                defer.complete(null)
            }
        } else {
            FirebaseHelper.logEvent(
                "recom_parse_fail_code_not_0", bundleOf(
                    "code" to code
                )
            )
            defer.complete(null)
        }
    }

    private fun parseRecommend(dataArray: JSONArray): ArrayList<RecommendBean> {
        val recommendList = ArrayList<RecommendBean>()
        for (i in 0 until dataArray.length()) {
            val data = dataArray.getJSONObject(i)
            val parseUrl = data.getString("or_url")
            val downloadUrl = data.getString("url")
            val coverPath = data.getString("img")
            val videoDescription = data.getString("title")
            val author = data.optString("author")
            val avatar = data.optString("avatar")
            val likeCount = data.optInt("like_count")
            val topics = data.optJSONArray("topics") ?: JSONArray()
            val topicStr = if (topics.length() > 0) {
                val topicList = ArrayList<String>()
                for (j in 0 until topics.length()) {
                    val topic = topics.getString(j)
                    topicList.add(topic)
                }
                topicList.joinToString(",")
            } else {
                ""
            }
            val videoStr = data.optJSONArray("videos")?.toString() ?: ""
            val audioStr = data.optJSONObject("audio")?.optString("url") ?: ""
            val recommend = RecommendBean(
                UUID.randomUUID().toString(),
                parseUrl,
                downloadUrl,
                coverPath,
                avatar,
                videoDescription,
                author,
                "$likeCount",
                topicStr
            )
            recommend.videoDuration = data.optLong("duration") * 1000
            recommend.videos = videoStr
            recommend.audio = audioStr
            recommendList.add(recommend)
        }
        return recommendList
    }

    private fun getPurchaseUserDefaultFunList(): ArrayList<RecommendBean> {
        return arrayListOf(
            RecommendBean(
                UUID.randomUUID().toString(),
                "https://instagram.com/lenkafrompoland/reel/DdKU8IctZEd/",
                "",
                "https://instagram.fmxp12-1.fna.fbcdn.net/v/t51.71878-15/805485549_1600062551831630_5513655628080531632_n.jpg?stp=dst-jpg_e15_p360x360_tt6&_nc_cat=1&ccb=7-5&_nc_sid=58cdad&efg=eyJ2ZW5jb2RlX3RhZyI6IkNMSVBTLnhwaWRzLjY0MC5zZHIudmlkZW9fYWRkaXRpb25hbF9jb3Zlcl9mcmFtZS5DMyJ9&_nc_ohc=cLt79BV1QV4Q7kNvwEKClxs&_nc_oc=Adr41R2HghTTsxFE2Dn6t8KK646xNFo_fmiAirAvXlc_LFFuz2k3aada7R8F3I2z7ZQ&_nc_ad=z-m&_nc_cid=1093&_nc_zt=23&_nc_ht=instagram.fmxp12-1.fna&_nc_gid=yeuIXrvUoKFqaF2X501g-w&_nc_ss=7a3ba&oh=00_AQI2Bbst3TM37jvbml-0KagXaTeWEi2W25_51CQUN9KZ-w&oe=6AB3B969",
                "",
                "",
                "",
                "",
                ""
            ).apply {
                videoDuration = 0
            },

            RecommendBean(
                UUID.randomUUID().toString(),
                "https://instagram.com/princessironfanfr/reel/DcnUecwo2nh/",
                "",
                "https://scontent-cdg6-1.cdninstagram.com/v/t51.71878-15/790240471_1043840318635830_7110096059809949269_n.jpg?stp=dst-jpg_e15_p480x480_tt6&_nc_cat=106&ccb=7-5&_nc_sid=58cdad&efg=eyJ2ZW5jb2RlX3RhZyI6IkNMSVBTLnhwaWRzLjY0MC5zZHIudmlkZW9fYWRkaXRpb25hbF9jb3Zlcl9mcmFtZS5DMyJ9&_nc_ohc=UTAVt1JTh1cQ7kNvwFQWegh&_nc_oc=AdqrwGiDHRPOohdXiANr2upb27-GmX_ZH94WjbF8sx-0k0hWR4gX5Bp_ydtNX0h62uY&_nc_ad=z-m&_nc_cid=0&_nc_zt=23&_nc_ht=scontent-cdg6-1.cdninstagram.com&_nc_gid=z4a1Ib2tDjwqAUXAjvWSAw&_nc_ss=7a3ba&oh=00_AQIxluuVmHhV8Iz48hWAznJW7_ycPIFThw_vQfnF2fw8-A&oe=6AB3ACF1",
                "",
                "#笑顔\uD83D\uDC95毎日の軽快なジャンプファッションコーデ\uD83D\uDC83\uD83C\uDFB6\uD83E\uDD70\uD83E\uDE77\uD83D\uDCAF✌\uFE0F",
                "",
                "",
                ""
            ).apply {
                videoDuration = 0
            },

            RecommendBean(
                UUID.randomUUID().toString(),
                "https://instagram.com/arnunoo/reel/DbnW8l6To66/",
                "",
                "https://instagram.fflr4-2.fna.fbcdn.net/v/t51.71878-15/763685035_1066334286129472_1790508648798143868_n.jpg?stp=dst-jpg_e15_p480x480_tt6&_nc_cat=111&_nc_map=urlgen_bucketless&ccb=7-5&_nc_sid=58cdad&efg=eyJ2ZW5jb2RlX3RhZyI6IkNMSVBTLnhwaWRzLjY0MC5zZHIudmlkZW9fYWRkaXRpb25hbF9jb3Zlcl9mcmFtZS5DMyJ9&_nc_ohc=q3D5mAKz1yoQ7kNvwE-75Ii&_nc_oc=AdodLMPkCX8l-PTnXs3QdFdAYCAaGitqE-JjlSFBXd3OvbGmJ7P-WWQBiCZcr0h59ow&_nc_ad=z-m&_nc_cid=1093&_nc_zt=23&_nc_ht=instagram.fflr4-2.fna&_nc_gid=e7yD5UnQKUSeP_R0M6ESCw&_nc_ss=7a3ba&oh=00_AQK6mZMiZhVqT2pmnrae79uOWtkIiiCk2PKwPON0MkfNKw&oe=6AB395BF",
                "",
                "ก็ตัวเล็กอ่า ต้องมีกำลังเสริม\uD83D\uDE02\uD83D\uDE02",
                "",
                "",
                ""
            ).apply {
                videoDuration = 0
            },

            RecommendBean(
                UUID.randomUUID().toString(),
                "https://instagram.com/restrepo_karin/reel/DdbzzqvOFBf/",
                "",
                "https://instagram.fflr4-1.fna.fbcdn.net/v/t51.71878-15/813861021_1343832218808952_496294319461869833_n.jpg?stp=dst-jpg_e15_p480x480_tt6&_nc_cat=108&_nc_map=urlgen_bucketless&ccb=7-5&_nc_sid=58cdad&efg=eyJ2ZW5jb2RlX3RhZyI6IkNMSVBTLnhwaWRzLjY0MC5zZHIudmlkZW9fYWRkaXRpb25hbF9jb3Zlcl9mcmFtZS5DMyJ9&_nc_ohc=LCthjX-O3d8Q7kNvwHaF4rk&_nc_oc=Adrc7gzRtkDeSeS0jdYqvaKOhn0gFTjSSTf8Kj7BQa3ujazHQpiln4mAxzu0KK8zNlY&_nc_ad=z-m&_nc_cid=1093&_nc_zt=23&_nc_ht=instagram.fflr4-1.fna&_nc_gid=C1mu2RtQOpsSlH9drzm6jw&_nc_ss=7a3ba&oh=00_AQLr9v1CR5AMKW-Wkm3ldntWeolAnG2m5l5BuuZ_IM9QGw&oe=6AB38D10",
                "",
                "Karina Restrepo",
                "",
                "",
                ""
            ).apply {
                videoDuration = 0
            },

            RecommendBean(
                UUID.randomUUID().toString(),
                "https://instagram.com/moojija258/reel/DcGWyKtkqU3/",
                "",
                "https://instagram.fflr4-2.fna.fbcdn.net/v/t51.82787-15/774761538_18420820642198173_8323920104554836001_n.jpg?stp=dst-jpg_e15_p480x480_tt6&_nc_cat=103&_nc_map=urlgen_bucketless&ccb=7-5&_nc_sid=58cdad&efg=eyJ2ZW5jb2RlX3RhZyI6IkNMSVBTLnhwaWRzLjEwODAuc2RyLnZpZGVvX2FkZGl0aW9uYWxfY292ZXJfZnJhbWUuQzMifQ%3D%3D&_nc_ohc=jpw6kbs7E3UQ7kNvwE7sMMl&_nc_oc=AdrbSQfKDLAhx4XWEGH7qVQtRzOWTeHSZID4o3cBfjqf8FSRBIeySDyAAoyaX_9ST9w&_nc_ad=z-m&_nc_cid=1093&_nc_zt=23&_nc_ht=instagram.fflr4-2.fna&_nc_gid=ErOTYoXJNscbPhLRVCdqYQ&_nc_ss=7a3ba&oh=00_AQKPWL2ZreDQKIRR83iMMUEiys4Zwz-Bo8MKReq5KOtFsA&oe=6AB3AA86",
                "",
                "ยุค90นี่เอง\uD83D\uDE0E #ยุค90\"",
                "",
                "",
                ""
            ).apply {
                videoDuration = 0
            },

            RecommendBean(
                UUID.randomUUID().toString(),
                "https://instagram.com/lenkafrompoland/reel/Dc1uciFN4GV/",
                "",
                "https://instagram.fmxp12-1.fna.fbcdn.net/v/t51.71878-15/793001642_1046117041373594_9039252605031897048_n.jpg?stp=dst-jpg_e15_p360x360_tt6&_nc_cat=111&_nc_map=urlgen_bucketless&ccb=7-5&_nc_sid=58cdad&efg=eyJ2ZW5jb2RlX3RhZyI6IkNMSVBTLnhwaWRzLjY0MC5zZHIudmlkZW9fYWRkaXRpb25hbF9jb3Zlcl9mcmFtZS5DMyJ9&_nc_ohc=AJdAyiIo61kQ7kNvwHk2yJr&_nc_oc=AdouNZ4mugJHNtQI9TREg6cBp9lHjaypDgpBBwpinKyzjVJCRXVkj1v2ySiTD8wFu4k&_nc_ad=z-m&_nc_cid=1093&_nc_zt=23&_nc_ht=instagram.fmxp12-1.fna&_nc_gid=yeuIXrvUoKFqaF2X501g-w&_nc_ss=7a3ba&oh=00_AQK0-ZldXBenZLKWD7QZJc0QMdyPCORr7tbPCF18ilGXMw&oe=6AB3B853",
                "",
                "Despite the rain it was a perfect hike",
                "",
                "",
                ""
            ).apply {
                videoDuration = 0
            },

            )
    }

}