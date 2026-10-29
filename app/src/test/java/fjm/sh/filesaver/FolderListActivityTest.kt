package fjm.sh.filesaver

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import fjm.sh.filesaver.TestSupport.context
import fjm.sh.filesaver.TestSupport.prefs
import fjm.sh.filesaver.TestSupport.root
import fjm.sh.filesaver.TestSupport.setDestinations
import fjm.sh.filesaver.activity.FolderListActivity
import fjm.sh.filesaver.activity.FolderSelectActivity
import fjm.sh.filesaver.ui.CardItem
import fjm.sh.filesaver.ui.CardListAdapter
import fjm.sh.filesaver.util.LocationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [23, 26, 28, 29, 30, 31, 33, 34, 35, 36])
class FolderListActivityTest {

	@Before
	fun setUp() {
		root().listFiles()?.forEach { it.deleteRecursively() }
		prefs.edit().clear().commit()
		// Mark the change log as seen
		de.cketti.library.changelog.ChangeLog(context).skipLogDialog()
	}

	private fun launch(): FolderListActivity {
		val activity = Robolectric.buildActivity(FolderListActivity::class.java).setup().get()
		TestSupport.idle()
		return activity
	}

	private fun items(activity: FolderListActivity): List<CardItem> =
		(activity.findViewById<RecyclerView>(R.id.list).adapter as CardListAdapter).items

	@Test
	fun noDestinations_showsEmptyState() {
		val activity = launch()
		val items = items(activity)
		assertEquals(1, items.size)
		assertTrue(items[0] is CardItem.Message)
		assertEquals(View.GONE, activity.findViewById<View>(R.id.fab).visibility)
	}

	@Test
	fun existingDestinations_areShownInStoredOrder() {
		setDestinations("/DCIM/Memes", "/Download", "")
		val activity = launch()

		val rows = items(activity).filterIsInstance<CardItem.Row>()
		assertEquals(listOf("/DCIM/Memes", "/Download", ""), rows.map { it.key })
		assertEquals("Memes", rows[0].title)
		assertEquals("/DCIM/Memes", rows[0].summary)
		assertEquals(File.separator, rows[2].summary)
		assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.fab).visibility)
	}

	@Test
	fun locationSelect_addsNonDeletableFirstRow() {
		setDestinations("/A")
		prefs.edit().putBoolean("location_select", true).commit()
		val rows = items(launch()).filterIsInstance<CardItem.Row>()
		assertEquals(2, rows.size)
		assertEquals(0, rows[0].actionIcon)
		assertTrue(rows[1].actionIcon != 0)
	}

	@Test
	@Suppress("DEPRECATION")
	fun folderSelectResult_addsSortedWithoutDuplicates() {
		setDestinations("/b", "/d")
		val activity = launch()

		val data = Intent().putExtra(FolderSelectActivity.FOLDER_SELECTED, LocationHelper.rootLocation + "/C")
		activity.onActivityResult(FolderListActivity.FOLDER_SELECT_REQUEST_CODE, Activity.RESULT_OK, data)
		activity.onActivityResult(FolderListActivity.FOLDER_SELECT_REQUEST_CODE, Activity.RESULT_OK, data)

		assertEquals(listOf("/b", "/C", "/d"), LocationHelper.loadFolderList(context))
		assertEquals(listOf("/b", "/C", "/d"), items(activity).filterIsInstance<CardItem.Row>().map { it.key })
	}

	@Test
	fun delete_asksForConfirmation_thenRemovesDestination() {
		setDestinations("/A", "/B")
		val activity = launch()
		val list = activity.findViewById<RecyclerView>(R.id.list)
		list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
			View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.AT_MOST))
		list.layout(0, 0, 1080, list.measuredHeight)

		// Position 0 is the section header
		list.findViewHolderForAdapterPosition(1)!!.itemView.findViewById<View>(R.id.action).performClick()
		TestSupport.idle()
		assertEquals("nothing removed before confirming", listOf("/A", "/B"), LocationHelper.loadFolderList(context))

		val dialog = ShadowDialog.getLatestDialog()
		assertNotNull(dialog)
		val confirm = findViewWithText(dialog.window!!.decorView, context.getString(R.string.folder_list_delete_confirm))
		assertNotNull("confirm button", confirm)
		confirm!!.performClick()
		TestSupport.idle()

		assertEquals(listOf("/B"), LocationHelper.loadFolderList(context))
		assertEquals(listOf("/B"), items(activity).filterIsInstance<CardItem.Row>().map { it.key })
	}

	@Test
	fun launch_showsNoSupportPopup() {
		launch()
		assertEquals(null, ShadowDialog.getLatestDialog())
	}

	@Test
	fun githubMenuItem_opensProfile() {
		val activity = launch()
		val toolbar = activity.findViewById<com.hchen.himiuix.MiuixAppBar>(R.id.appbar).toolbar
		assertEquals(null, toolbar.menu.findItem(context.resources.getIdentifier("action_support", "id", context.packageName)))
		val item = toolbar.menu.findItem(R.id.action_github)
		assertEquals("fjmd3v", item.title.toString())
		assertNotNull(item.icon)

		activity.onOptionsItemSelected(item)
		val started = org.robolectric.Shadows.shadowOf(activity).nextStartedActivity
		assertEquals(Intent.ACTION_VIEW, started.action)
		assertEquals("https://github.com/fjmd3v", started.dataString)
	}

	@Test
	fun addButton_opensFolderSelect() {
		setDestinations("/A")
		val activity = launch()
		activity.findViewById<View>(R.id.fab).performClick()
		val next = org.robolectric.Shadows.shadowOf(activity).nextStartedActivityForResult
		assertEquals(FolderSelectActivity::class.java.name, next.intent.component!!.className)
		assertEquals(FolderListActivity.FOLDER_SELECT_REQUEST_CODE, next.requestCode)
	}

	@Test
	@Config(qualifiers = "night")
	fun darkTheme_inflates() {
		setDestinations("/A")
		val activity = launch()
		assertTrue(activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
				android.content.res.Configuration.UI_MODE_NIGHT_YES)
		assertEquals(2, items(activity).size)
		// Dark backgrounds are #131313, not pure black
		assertEquals(0xFF131313.toInt(), androidx.core.content.ContextCompat.getColor(activity, R.color.fs_window_background))
	}

	private fun findViewWithText(view: View, text: String): View? {
		if (view is TextView && view.text.toString() == text) return view
		if (view is ViewGroup) {
			for (i in 0 until view.childCount) {
				findViewWithText(view.getChildAt(i), text)?.let { return it }
			}
		}
		return null
	}
}
