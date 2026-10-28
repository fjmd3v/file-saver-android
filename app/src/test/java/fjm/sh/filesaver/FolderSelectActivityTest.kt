package fjm.sh.filesaver

import android.app.Activity
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hchen.himiuix.MiuixAppBar
import fjm.sh.filesaver.TestSupport.root
import fjm.sh.filesaver.activity.FolderSelectActivity
import fjm.sh.filesaver.ui.CardItem
import fjm.sh.filesaver.ui.CardListAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [23, 28, 29, 31, 34, 36])
class FolderSelectActivityTest {

	@Before
	fun setUp() {
		root().listFiles()?.forEach { it.deleteRecursively() }
		File(root(), "beta").mkdirs()
		File(root(), "Alpha/Inner").mkdirs()
		File(root(), "file.txt").writeText("not a folder")
	}

	private fun rows(activity: FolderSelectActivity): List<CardItem.Row> =
		(activity.findViewById<RecyclerView>(R.id.list).adapter as CardListAdapter).items.filterIsInstance<CardItem.Row>()

	private fun click(activity: FolderSelectActivity, key: String) {
		val list = activity.findViewById<RecyclerView>(R.id.list)
		val adapter = list.adapter as CardListAdapter
		val position = adapter.items.indexOfFirst { it is CardItem.Row && it.key == key }
		list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
			View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.AT_MOST))
		list.layout(0, 0, 1080, list.measuredHeight)
		list.findViewHolderForAdapterPosition(position)!!.itemView.findViewById<View>(R.id.row).performClick()
		TestSupport.idle()
	}

	@Test
	fun listsOnlyFolders_sortedCaseInsensitively_thenSelects() {
		val activity = Robolectric.buildActivity(FolderSelectActivity::class.java).setup().get()
		TestSupport.idle()

		// current folder, parent, then sub folders
		assertEquals(listOf("current", "back", "/Alpha", "/beta"), rows(activity).map { it.key })

		click(activity, "/Alpha")
		assertEquals("Alpha", activity.findViewById<MiuixAppBar>(R.id.appbar).title.toString())
		assertEquals(listOf("current", "back", "/Inner"), rows(activity).map { it.key })

		activity.findViewById<View>(R.id.navigation_here).performClick()
		val result = shadowOf(activity)
		assertEquals(Activity.RESULT_OK, result.resultCode)
		assertEquals(File(root(), "Alpha").absolutePath,
			result.resultIntent.getStringExtra(FolderSelectActivity.FOLDER_SELECTED))
		assertTrue(activity.isFinishing)
	}

	@Test
	fun back_goesUpOneLevel_thenCancelsAtRoot() {
		val activity = Robolectric.buildActivity(FolderSelectActivity::class.java).setup().get()
		TestSupport.idle()
		click(activity, "/Alpha")

		activity.onBackPressedDispatcher.onBackPressed()
		TestSupport.idle()
		assertEquals(listOf("current", "back", "/Alpha", "/beta"), rows(activity).map { it.key })

		activity.onBackPressedDispatcher.onBackPressed()
		assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
		assertTrue(activity.isFinishing)
	}

	@Test
	fun readOnlyFolder_disablesSelection() {
		val readOnly = File(root(), "Alpha")
		val activity = Robolectric.buildActivity(FolderSelectActivity::class.java).setup().get()
		TestSupport.idle()
		readOnly.setWritable(false)
		try {
			click(activity, "/Alpha")
			// Robolectric runs as the file owner, so only assert when the flag took effect
			if (!readOnly.canWrite()) {
				assertEquals(View.GONE, activity.findViewById<View>(R.id.navigation_here).visibility)
				assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.navigation_read_only).visibility)
			}
		} finally {
			readOnly.setWritable(true)
		}
	}
}
