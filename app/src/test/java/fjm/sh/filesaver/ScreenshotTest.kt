package fjm.sh.filesaver

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import fjm.sh.filesaver.TestSupport.context
import fjm.sh.filesaver.TestSupport.prefs
import fjm.sh.filesaver.TestSupport.root
import fjm.sh.filesaver.TestSupport.setDestinations
import fjm.sh.filesaver.activity.CreditsActivity
import fjm.sh.filesaver.activity.FolderListActivity
import fjm.sh.filesaver.activity.FolderSelectActivity
import fjm.sh.filesaver.activity.SaverActivity
import fjm.sh.filesaver.activity.SettingsActivity
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the main screens to PNG files for visual review (light and dark).
 * Only runs when -Pscreenshots is passed:
 *   ./gradlew testDebugUnitTest --tests '*ScreenshotTest*' -Pscreenshots -ProbolectricSdks=33
 * Output: app/build/screenshots/
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

	private val outDir = File(System.getProperty("screenshotDir") ?: "build/screenshots")

	@Before
	fun setUp() {
		assumeTrue(System.getProperty("screenshotDir") != null)
		outDir.mkdirs()
		root().listFiles()?.forEach { it.deleteRecursively() }
		prefs.edit().clear().putBoolean("hide_support", true).commit()
		de.cketti.library.changelog.ChangeLog(context).skipLogDialog()
	}

	private fun capture(activity: Activity, name: String) {
		TestSupport.idle()
		val view = activity.window.decorView
		view.measure(View.MeasureSpec.makeMeasureSpec(1233, View.MeasureSpec.EXACTLY),
			View.MeasureSpec.makeMeasureSpec(2673, View.MeasureSpec.EXACTLY))
		view.layout(0, 0, 1233, 2673)
		TestSupport.idle()
		val bitmap = Bitmap.createBitmap(1233, 2673, Bitmap.Config.ARGB_8888)
		view.draw(android.graphics.Canvas(bitmap))
		File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
	}

	private fun captureView(view: View, name: String) {
		TestSupport.idle()
		val width = view.width.takeIf { it > 0 } ?: 1233
		val height = view.height.takeIf { it > 0 } ?: 2673
		val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
		bitmap.eraseColor(0xFF888888.toInt())
		view.draw(android.graphics.Canvas(bitmap))
		File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
	}

	private fun all(suffix: String) {
		capture(Robolectric.buildActivity(FolderListActivity::class.java).setup().get(), "01-empty$suffix")

		setDestinations("/DCIM/Memes", "/Download", "/Pictures/Reaction images", "/Documents/Receipts")
		capture(Robolectric.buildActivity(FolderListActivity::class.java).setup().get(), "02-destinations$suffix")

		capture(Robolectric.buildActivity(SettingsActivity::class.java).setup().get(), "03-settings$suffix")

		File(root(), "Alpha").mkdirs(); File(root(), "Movies").mkdirs(); File(root(), "Music").mkdirs()
		capture(Robolectric.buildActivity(FolderSelectActivity::class.java).setup().get(), "04-folder-select$suffix")

		val share = Intent(context, SaverActivity::class.java).setAction(Intent.ACTION_SEND)
			.setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Some shared text")
		val saver = Robolectric.buildActivity(SaverActivity::class.java, share).setup().get()
		TestSupport.waitFor { saver.findViewById<View>(R.id.list).visibility == View.VISIBLE }
		// Let the slide-in animation finish
		org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2))
		capture(saver, "05-share-sheet$suffix")

		val list = Robolectric.buildActivity(FolderListActivity::class.java).setup().get()
		fjm.sh.filesaver.util.DialogHelper.showConfirmDialog(list,
			context.getString(R.string.folder_list_delete_title),
			context.getString(R.string.folder_list_delete_message, "/DCIM/Memes"),
			context.getString(R.string.folder_list_delete_confirm)) {}
		org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2))
		val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
		captureView(dialog.window!!.decorView, "07-confirm-dialog$suffix")

		capture(Robolectric.buildActivity(CreditsActivity::class.java).setup().get(), "06-credits$suffix")
	}

	@Test
	fun light() = all("")

	@Test
	@Config(qualifiers = "+night")
	fun dark() = all("-dark")
}
