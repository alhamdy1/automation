package com.passphoto.processor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test for application context
 */
@RunWith(AndroidJUnit4::class)
class ApplicationInstrumentedTest {

    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.passphoto.processor", appContext.packageName)
    }
    
    @Test
    fun appNameIsCorrect() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val appName = appContext.getString(appContext.applicationInfo.labelRes)
        assertEquals("Pass Photo Processor", appName)
    }
}
