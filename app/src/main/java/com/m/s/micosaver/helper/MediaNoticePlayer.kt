package com.m.s.micosaver.helper

import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
internal class MediaNoticePlayer(player: Player) : ForwardingPlayer(player) {
    override fun getAvailableCommands(): Player.Commands = READ_ONLY_COMMANDS

    override fun isCommandAvailable(command: Int): Boolean = READ_ONLY_COMMANDS.contains(command)

    override fun getDuration(): Long = C.TIME_UNSET

    override fun getContentDuration(): Long = C.TIME_UNSET

    override fun getCurrentPosition(): Long = 0L

    override fun getContentPosition(): Long = 0L

    override fun getBufferedPosition(): Long = 0L

    override fun getContentBufferedPosition(): Long = 0L

    override fun getBufferedPercentage(): Int = 0

    override fun getTotalBufferedDuration(): Long = 0L

    override fun isCurrentMediaItemSeekable(): Boolean = false

    private companion object {
        val READ_ONLY_COMMANDS: Player.Commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_GET_AUDIO_ATTRIBUTES,
                Player.COMMAND_GET_VOLUME,
                Player.COMMAND_GET_DEVICE_VOLUME,
                Player.COMMAND_GET_TEXT,
                Player.COMMAND_GET_TRACKS,
            )
            .build()
    }
}
