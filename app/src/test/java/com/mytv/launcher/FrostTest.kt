package com.mytv.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Phase 5: the frosted glass follows the picture that is really on the home screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FrostTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun reset() = Frost.setSource(null)

    private fun solid(color: Int, w: Int = 1920, h: Int = 1080): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.eraseColor(color) }

    @Test
    fun builtInPhotoIsTheDefaultAndResettingItDoesNotChurn() {
        val v = Frost.version
        Frost.setSource(null)
        assertEquals("nothing changed, so no rebuild", v, Frost.version)
        assertNotNull(Frost.bitmap(ctx))
        assertNotNull(Frost.shader(ctx))
    }

    @Test
    fun customPictureReplacesTheGlassSourceAndCanBeReset() {
        val builtIn = Frost.bitmap(ctx)
        val v0 = Frost.version

        Frost.setSource(solid(Color.WHITE))
        assertTrue("version bumped so glass surfaces rebuild", Frost.version > v0)
        val custom = Frost.bitmap(ctx)
        assertNotNull(custom)
        assertNotSame(builtIn, custom)
        // ~1/16 of the screen, never the full-size picture.
        assertTrue("small copy (${custom!!.width}x${custom.height})", custom.width <= 240 && custom.width >= 100)
        assertNotNull(Frost.shader(ctx))

        val v1 = Frost.version
        Frost.setSource(null)
        assertTrue(Frost.version > v1)
        assertTrue("built-in photo is back", Frost.bitmap(ctx)!!.width > 0)
    }

    @Test
    fun luminanceFollowsThePicture() {
        Frost.setSource(solid(Color.WHITE))
        val bright = Frost.luminance(ctx)
        Frost.setSource(solid(Color.BLACK))
        val dark = Frost.luminance(ctx)
        assertTrue("white=$bright black=$dark", bright > 0.9f && dark < 0.1f)
    }

    @Test
    fun smallPicturesAreAccepted() {
        Frost.setSource(solid(Color.RED, 64, 36))
        assertNotNull(Frost.bitmap(ctx))
        assertEquals(64, Frost.bitmap(ctx)!!.width)
    }
}
