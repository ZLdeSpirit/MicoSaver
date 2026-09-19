package com.m.s.micosaver.helper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileChangeMonitorTest {
    @Test
    fun recognizesCommonScreenshotNamesAndDirectories() {
        assertTrue(FileChangeMonitor.isScreenshotPath("Pictures/Screenshots/IMG_1.png"))
        assertTrue(FileChangeMonitor.isScreenshotPath("DCIM/Screen_capture_1.jpg"))
        assertTrue(FileChangeMonitor.isScreenshotPath("图片/截图_2026.png"))
    }

    @Test
    fun ordinaryFilesAreNotScreenshots() {
        assertFalse(FileChangeMonitor.isScreenshotPath("Download/report.pdf"))
        assertFalse(FileChangeMonitor.isScreenshotPath(null))
    }
}
