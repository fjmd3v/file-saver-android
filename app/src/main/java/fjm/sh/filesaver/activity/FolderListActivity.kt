package fjm.sh.filesaver.activity

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.view.menu.MenuBuilder
import androidx.core.view.MenuItemCompat
import androidx.recyclerview.widget.RecyclerView
import fjm.sh.filesaver.R
import fjm.sh.filesaver.ui.CardItem
import fjm.sh.filesaver.ui.CardListAdapter
import fjm.sh.filesaver.ui.MiuixListFragment
import fjm.sh.filesaver.util.ChangeLogHelper
import fjm.sh.filesaver.util.DebugLogger
import fjm.sh.filesaver.util.DialogHelper
import fjm.sh.filesaver.util.DirectShareHelper
import fjm.sh.filesaver.util.LocationHelper
import fjm.sh.filesaver.util.PreferenceHelper
import fjm.sh.filesaver.util.SaveNotifier
import java.io.File
import java.util.Locale

/**
 * The entry point activity.
 * This activity shows the list of user selected folders.
 */
class FolderListActivity : BaseActivity(), MiuixListFragment.Host {

	companion object {
		const val FOLDER_SELECT_REQUEST_CODE = 1
		const val PERMISSION_REQUEST_CODE = 2

		private const val KEY_LOCATION_SELECT = "location_select"
	}

	private lateinit var log: DebugLogger

	private val folderList: MutableList<String> = ArrayList()

	private val preferenceHelper = PreferenceHelper(this)

	private val adapter = CardListAdapter(
		onRowAction = { row, _ -> confirmDelete(row.key) },
		onMessageAction = { openFolderSelect() },
	)

	private lateinit var fab: View

	override fun onCreate(savedInstanceState: android.os.Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.folder_list_activity)

		val appBar = setUpAppBar(getString(R.string.app_name), showUp = false)
		appBar.toolbar.inflateMenu(R.menu.folder_list_menu)
		appBar.toolbar.setOnMenuItemClickListener { onOptionsItemSelected(it) }
		showOverflowMenuIcons(appBar.toolbar.menu)

		fab = findViewById(R.id.fab)
		fab.setOnClickListener { openFolderSelect() }
		applyBottomInset(fab, asMargin = true)

		log = DebugLogger(this)

		// Check for permissions
		testPermissions()

		// Save result notifications. On Android 13+ creating the channel while this screen is
		// visible lets the system ask for the notification permission here, not while sharing.
		SaveNotifier.createChannel(this)

		preferenceHelper.loadPreferences()

		// Show the change log when there is something new
		ChangeLogHelper.show(this, false)

		// Keep Direct Share targets in sync for users upgrading from older versions
		DirectShareHelper.publish(this, LocationHelper.loadFolderList(this).orEmpty())
	}

	override fun onListViewCreated(recyclerView: RecyclerView) {
		recyclerView.adapter = adapter
	}

	override fun onResume() {
		super.onResume()

		preferenceHelper.loadPreferences()

		// Init list items
		loadFolderList()
	}

	private fun openFolderSelect() {
		val intent = Intent(this@FolderListActivity, FolderSelectActivity::class.java)
		@Suppress("DEPRECATION")
		this@FolderListActivity.startActivityForResult(intent, FOLDER_SELECT_REQUEST_CODE)
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean =
		when (item.itemId) {
			R.id.action_credits -> {
				startActivity(Intent(this, CreditsActivity::class.java))
				true
			}
			R.id.action_settings -> {
				startActivity(Intent(this, SettingsActivity::class.java))
				true
			}
			R.id.action_github -> {
				openGitHub()
				true
			}
			R.id.action_change_log -> {
				ChangeLogHelper.show(this, true)
				true
			}
			else ->
				super.onOptionsItemSelected(item)
		}

	@Deprecated("Deprecated in Java")
	public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		@Suppress("DEPRECATION")
		super.onActivityResult(requestCode, resultCode, data)
		if (data == null){
			return
		}
		if (resultCode == RESULT_OK) {
			if (requestCode == FOLDER_SELECT_REQUEST_CODE) {
				val selected = data.getStringExtra(FolderSelectActivity.FOLDER_SELECTED) ?: return
				val folder = LocationHelper.removeRoot(selected)
				// Don't add duplicates
				if (!folderList.contains(folder)) {
					folderList.add(folder)
					folderList.sortBy { it.lowercase(Locale.getDefault()) }
					LocationHelper.saveFolderList(this, folderList)
					showFolderList()
				}
			}
		}
	}

	/**
	 * Overflow menus hide item icons by default; show them so the GitHub item has its logo.
	 * Items without an icon are unaffected.
	 */
	@SuppressLint("RestrictedApi")
	private fun showOverflowMenuIcons(menu: Menu) {
		(menu as? MenuBuilder)?.setOptionalIconsVisible(true)
		menu.findItem(R.id.action_github)?.let {
			MenuItemCompat.setContentDescription(it, getString(R.string.action_github_description))
		}
	}

	/**
	 * Opens the fjmd3v GitHub profile in the browser.
	 */
	private fun openGitHub() {
		val url = getString(R.string.github_url)
		try {
			startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
		} catch (e: ActivityNotFoundException) {
			Toast.makeText(this, getString(R.string.toast_no_browser, url), Toast.LENGTH_LONG).show()
		}
	}

	/**
	 * Loads the list of folder paths.
	 */
	private fun loadFolderList(){
		folderList.clear()

		LocationHelper.loadFolderList(this)?.let {
			folderList.addAll(it)
		}
		showFolderList()
	}

	/**
	 * Shows the destinations, or the empty state when there is nothing to show.
	 */
	private fun showFolderList() {
		if (!PreferenceHelper.locationSelectEnabled && folderList.isEmpty()) {
			adapter.items = listOf(CardItem.Message(
				icon = R.drawable.fs_ic_folder_add,
				title = getString(R.string.folder_list_empty_title),
				body = getString(R.string.folder_list_empty_body),
				actionText = getString(R.string.folder_list_add_destination),
			))
			// The empty state has its own add button
			fab.visibility = View.GONE
			return
		}

		fab.visibility = View.VISIBLE
		val items = mutableListOf<CardItem>(CardItem.Header(getString(R.string.folder_list_section_destinations)))
		if (PreferenceHelper.locationSelectEnabled) {
			items.add(CardItem.Row(
				key = KEY_LOCATION_SELECT,
				title = getString(R.string.location_select_title),
				summary = getString(R.string.location_select_summary),
				icon = R.drawable.fs_ic_document,
				clickable = false,
			))
		}
		folderList.forEach { folder ->
			val name = displayName(folder)
			items.add(CardItem.Row(
				key = folder,
				title = name,
				summary = displayPath(folder),
				clickable = false,
				actionIcon = R.drawable.fs_ic_delete,
				actionDescription = getString(R.string.folder_list_delete_description, name),
			))
		}
		adapter.items = items
	}

	private fun displayName(folder: String): String =
		folder.substringAfterLast(File.separatorChar).ifBlank { getString(R.string.folder_list_root_folder) }

	private fun displayPath(folder: String): String = if (folder.isBlank()) File.separator else folder

	/**
	 * Asks for confirmation, then removes a destination.
	 */
	private fun confirmDelete(folder: String) {
		DialogHelper.showConfirmDialog(
			this,
			title = getString(R.string.folder_list_delete_title),
			message = getString(R.string.folder_list_delete_message, displayPath(folder)),
			confirmText = getString(R.string.folder_list_delete_confirm),
		) {
			if (folderList.remove(folder)) {
				LocationHelper.saveFolderList(this, folderList)
				showFolderList()
			}
		}
	}

	/**
	 * Permissions checker
	 */
	private fun testPermissions() {
		if (Build.VERSION.SDK_INT >= 23) {
			val permissions = listOf(
					// All available permissions
					android.Manifest.permission.READ_EXTERNAL_STORAGE,
					android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
					android.Manifest.permission.INTERNET
				// Filter out granted permissions
			).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()
			if (permissions.isNotEmpty()){
				log.i("Requesting permission for " + permissions.reduce { total, next -> "$total, $next" })
				// Request permissions
				requestPermissions(permissions, PERMISSION_REQUEST_CODE)
			}
		} else {
			// Permission is automatically granted on sdk<23 upon installation
			log.v("Permission is granted")
		}
	}
}
