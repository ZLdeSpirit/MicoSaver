package com.m.s.micosaver.helper

object ParamsHelper {
    const val KEY_ENTER_TYPE = "ms_enter_type"
    const val KEY_VIDEO_PATH = "ms_video_path"
    const val KEY_PARSE_URL = "ms_parse_url"
    const val KEY_MSG_ID = "ms_msg_id"
    const val KEY_PARSE_DESC = "ms_parse_desc"
    const val KEY_IS_LIKE = "ms_is_like"

    enum class EnterType(val type: String) {
        SAVING("ms_saving"),
        SAVED("ms_saved"),
        PARSE("ms_parse"),
        HOT_OPEN("ms_hot_open"),
        SHARE("ms_share"),
        UNKNOWN("ms_unknown")
    }

    enum class FromParse(val from: String) {
        MSG("ms_msg"),
        DEFAULT("ms_default"),
        SHARE("ms_share"),
        PASTE("ms_paste")
    }
}