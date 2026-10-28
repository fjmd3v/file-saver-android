package fjm.sh.filesaver.activity

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.recyclerview.widget.RecyclerView
import com.hchen.himiuix.MiuixAppBar
import java.io.File
import fjm.sh.filesaver.R.*
import fjm.sh.filesaver.ui.CardItem
import fjm.sh.filesaver.ui.CardListAdapter
import fjm.sh.filesaver.ui.MiuixListFragment
import fjm.sh.filesaver.util.DebugLogger
import fjm.sh.filesaver.util.LocationHelper
import java.util.Locale

/**
 * An activity for selecting a folder in the file system.
 */
class FolderSelectActivity : BaseActivity(), MiuixListFragment.Host {

	companion object {
		const val FOLDER_SELECTED = "FolderSelected"

		private const val KEY_CURRENT = "current"
		private const val KEY_BACK = "back"
		private const val KEY_EMPTY = "empty"
		private const val STATE_PATH = "currentPath"
	}

	private lateinit var log: DebugLogger

	@Suppress("DEPRECATION")
	private var currentPath: String = Environment.getExternalStorageDirectory().absolutePath

	private lateinit var appBar: MiuixAppBar

	private var listView: RecyclerView? = null

	private var folderList: List<String> = ArrayList()

	private val adapter = CardListAdapter(onRowClick = { row, _ ->
		if (row.key == KEY_BACK) {
			val back = getBackLocation()
			if (back == null) {
				cancelIntent()
			} else {
				currentPath = back
			}
		} else {
			currentPath += row.key
		}
		updateListView()
		listView?.scrollToPosition(0)
	})

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(layout.folder_select_activity)
		savedInstanceState?.getString(STATE_PATH)?.let { currentPath = it }

		log = DebugLogger(this)

		appBar = setUpAppBar(getString(string.title_activity_folder_select), showUp = true)

		// Init bottom buttons
		findViewById<View>(id.navigation_here).setOnClickListener {
			// Call back to previous activity with the location
			val intent = Intent()
			intent.putExtra(FOLDER_SELECTED, currentPath)
			setResult(RESULT_OK, intent)
			finish()
		}
		applyBottomInset(findViewById(id.navigation), asMargin = false)

		/**
		 * Goes up a directory, unless at the top, then exits
		 */
		onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
			override fun handleOnBackPressed() {
				val back = getBackLocation()
				if (back == null || currentPath == LocationHelper.rootLocation) {
					cancelIntent()
				} else {
					currentPath = back
					updateListView()
				}
			}
		})
	}

	override fun onListViewCreated(recyclerView: RecyclerView) {
		listView = recyclerView
		recyclerView.adapter = adapter
	}

	override fun onStart() {
		super.onStart()
		updateListView()
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		outState.putString(STATE_PATH, currentPath)
	}

	/**
	 * Update the folder list view
	 */
	private fun updateListView() {
		log.v("Path is: $currentPath")

		val currentFile = File(currentPath)

		val fList = currentFile.listFiles()
				// Directories only
				?.filter { it.isDirectory }
				// Get the file path
				?.map { removeCurrent(it.absolutePath) }
				// Sort it
				?.sortedBy { it.lowercase(Locale.getDefault()) }
				// Convert to mutable
				?.toMutableList()
				// Default to empty
				?: ArrayList()

		folderList = fList.toList()
		log.d("List length: "+folderList.size)

		// Set title
		val relativePath = LocationHelper.removeRoot(currentPath)
		this.title = relativePath + File.separatorChar
		appBar.title = relativePath.substringAfterLast(File.separatorChar)
				.ifBlank { getString(string.folder_list_root_folder) }

		val items = mutableListOf<CardItem>(
			CardItem.Row(
				key = KEY_CURRENT,
				title = getString(string.folder_select_current),
				summary = this.title,
				icon = drawable.fs_ic_folder,
				clickable = false,
			),
			CardItem.Header(getString(string.title_activity_folder_select)),
			// Back button as first item
			CardItem.Row(
				key = KEY_BACK,
				title = getString(string.folder_select_up),
				icon = drawable.fs_ic_arrow_upward,
			),
		)
		folderList.forEach {
			val name = it.trimStart(File.separatorChar)
			items.add(CardItem.Row(
				key = it,
				title = name,
				showChevron = true,
				clickDescription = getString(string.folder_select_open_description, name),
			))
		}
		if (folderList.isEmpty()) {
			items.add(CardItem.Row(
				key = KEY_EMPTY,
				title = getString(string.folder_select_empty),
				icon = drawable.fs_ic_folder,
				clickable = false,
			))
		}
		adapter.items = items

		updateWritable(currentFile.canWrite())
	}

	/**
	 * Updates the menu items based on whether the directory is writable or not.
	 */
	private fun updateWritable(writable: Boolean){
		findViewById<View>(id.navigation_here).visibility = if (writable) View.VISIBLE else View.GONE
		findViewById<View>(id.navigation_read_only).visibility = if (writable) View.GONE else View.VISIBLE
	}

	/**
	 * Remove the current location from the given path.
	 */
	private fun removeCurrent(location: String): String =
		location.replace(currentPath, "")

	/**
	 * Cancels the current activity
	 */
	private fun cancelIntent() {
		val intent = Intent()
		intent.putExtra(FOLDER_SELECTED, currentPath)
		setResult(RESULT_CANCELED, intent)
		finish()
	}

	/**
	 * Returns the back location for the current path
	 */
	private fun getBackLocation(): String? =
		if (currentPath.contains(File.separatorChar)) {
			currentPath.substring(0, currentPath.lastIndexOf(File.separatorChar))
		} else {
			null
		}

}
