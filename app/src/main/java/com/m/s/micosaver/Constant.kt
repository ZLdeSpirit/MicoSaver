package com.m.s.micosaver

object Constant {

    val TOPIC = listOf("MicoSaverTest")

    //TODO修改热云key
    const val RE_YUN_KEY = "N2Q5OWFmMWNjMzY1ZjU0Yw=="

    //TODO修改j解析链接
//    const val ANALYSIS_URL = "https://test.twothreemedia.xyz/vi/dpl?yiu="//DEBUG
    const val ANALYSIS_URL = "https://api.twothreemedia.xyz/vi/dpl?yiu="//RELEASE

    //TODO token上传
//    const val UPLOAD_TOKEN_URL = "https://test.twothreemedia.xyz/check/cok/"//DEBUG
    const val UPLOAD_TOKEN_URL = "https://api.twothreemedia.xyz/check/cok/"//RELEASE

    const val API_URL = "https://api.twothreemedia.xyz/check/ka/"//RELEASE

    const val BASE_RECOMMEND_URL = "https://test.twothreemedia.xyz/vi/res?k=20"// test
//    const val BASE_RECOMMEND_URL = "https://api.twothreemedia.xyz/vi/res?k=20"// release
}