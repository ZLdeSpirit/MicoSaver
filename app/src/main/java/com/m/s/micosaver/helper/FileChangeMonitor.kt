package com.m.s.micosaver.helper

import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.m.s.micosaver.ms
import java.io.File

object FileChangeMonitor {
    private const val CHANGE_DELAY = 10_000L
    private val observers = mutableMapOf<String, FileObserver>()

    fun start() {
        observeMediaStore()
        buildList {
            add(ms.filesDir)
            ms.getExternalFilesDirs(null).filterNotNullTo(this)
        }.forEach(::observeDirectoryTree)
    }

    private fun observeMediaStore() {
        val uri = MediaStore.Files.getContentUri("external")
        ms.contentResolver.registerContentObserver(
            uri,
            true,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, changedUri: Uri?) {
                    handleMediaChange(changedUri)
                }

                override fun onChange(selfChange: Boolean, changedUri: Uri?, flags: Int) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
                        flags and (android.content.ContentResolver.NOTIFY_INSERT or
                            android.content.ContentResolver.NOTIFY_DELETE) != 0
                    ) {
                        handleMediaChange(changedUri)
                    }
                }
            },
        )
    }

    private fun handleMediaChange(uri: Uri?) {
        val path = queryPath(uri)
        val scene = if (isScreenshotPath(path)) {
            SceneNotificationManager.Scene.SCREENSHOT
        } else {
            SceneNotificationManager.Scene.FILE_CHANGED
        }
        SceneNotificationManager.schedule(scene, CHANGE_DELAY)
    }

    private fun queryPath(uri: Uri?): String? {
        if (uri == null) return null
        val projection = buildList {
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.MediaColumns.DATA)
            }
        }.toTypedArray()
        return try {
            ms.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                projection.joinToString("/") { column ->
                    cursor.getString(cursor.getColumnIndexOrThrow(column)).orEmpty()
                }
            }
        } catch (e: Exception) {
            Log.i(SceneNotificationManager.TAG, "read changed media path failed", e)
            null
        }
    }

    private fun observeDirectoryTree(directory: File) {
        if (!directory.exists() || !directory.isDirectory) return
        observeDirectory(directory)
        directory.listFiles()?.filter(File::isDirectory)?.forEach(::observeDirectoryTree)
    }

    private fun observeDirectory(directory: File) {
        val path = directory.absolutePath
        if (observers.containsKey(path)) return
        val observer = object : FileObserver(
            directory.absolutePath,
            CREATE or MOVED_TO or DELETE or MOVED_FROM,
        ) {
            override fun onEvent(event: Int, childPath: String?) {
                if (childPath == null) return
                val changedFile = File(directory, childPath)
                if (event and (CREATE or MOVED_TO) != 0 && changedFile.isDirectory) {
                    observeDirectoryTree(changedFile)
                }
                val scene = if (isScreenshotPath(changedFile.absolutePath)) {
                    SceneNotificationManager.Scene.SCREENSHOT
                } else {
                    SceneNotificationManager.Scene.FILE_CHANGED
                }
                SceneNotificationManager.schedule(scene, CHANGE_DELAY)
            }
        }
        observers[path] = observer
        observer.startWatching()
    }

    internal fun isScreenshotPath(path: String?): Boolean {
        val normalized = path?.lowercase() ?: return false
        return SCREENSHOT_MARKERS.any(normalized::contains)
    }

    private val SCREENSHOT_MARKERS = listOf(
        "screenshot",
        "screen_shot",
        "screen-shot",
        "screen capture",
        "screen_capture",
        "screencapture",
        "截图",
        "截屏",
    )
}
