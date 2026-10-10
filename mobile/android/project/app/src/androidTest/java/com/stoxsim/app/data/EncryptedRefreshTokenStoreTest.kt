package com.stoxsim.app.data

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EncryptedRefreshTokenStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = EncryptedRefreshTokenStore(context)
    @Before fun setup() { store.clear() }
    @After fun teardown() { store.clear() }

    @Test fun secretIsEncryptedSurvivesRecreationAndClears() {
        store.write("synthetic-refresh-secret")
        val raw = context.getSharedPreferences("native_session", Context.MODE_PRIVATE).getString("refresh", null)!!
        assertFalse(raw.contains("synthetic-refresh-secret"))
        assertEquals("synthetic-refresh-secret", EncryptedRefreshTokenStore(context).read())
        store.clear()
        assertNull(EncryptedRefreshTokenStore(context).read())
    }

    @Test fun corruptCiphertextRequiresNewSignIn() {
        store.write("synthetic-refresh-secret")
        context.getSharedPreferences("native_session", Context.MODE_PRIVATE).edit().putString("refresh", "corrupted").commit()
        assertNull(store.read())
        assertTrue(context.getSharedPreferences("native_session", Context.MODE_PRIVATE).all.isEmpty())
    }
}
