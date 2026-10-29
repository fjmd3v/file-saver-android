package fjm.sh.filesaver

import android.util.TypedValue
import android.view.accessibility.AccessibilityNodeInfo
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hchen.himiuix.MiuixSwitchView
import com.hchen.himiuix.utils.MiuiSuperBlur
import fjm.sh.filesaver.TestSupport.context
import fjm.sh.filesaver.TestSupport.prefs
import fjm.sh.filesaver.activity.FolderListActivity
import fjm.sh.filesaver.util.BlurSupport
import fjm.sh.filesaver.util.ThemeHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class AppearanceTest {

	@Before
	fun setUp() {
		prefs.edit().clear().commit()
		de.cketti.library.changelog.ChangeLog(context).skipLogDialog()
	}

	private fun accentOf(activity: FolderListActivity): Int {
		val value = TypedValue()
		assertTrue(activity.theme.resolveAttribute(R.attr.fsColorAccent, value, true))
		return value.data
	}

	@Test
	fun themePreference_mapsToNightModes() {
		assertEquals(AppCompatDelegate.MODE_NIGHT_NO, ThemeHelper.nightModeFor("light"))
		assertEquals(AppCompatDelegate.MODE_NIGHT_YES, ThemeHelper.nightModeFor("dark"))
		assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, ThemeHelper.nightModeFor("system"))
		assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, ThemeHelper.nightModeFor(null))
	}

	@Test
	fun themePreference_isApplied() {
		prefs.edit().putString(ThemeHelper.PREF_THEME, "dark").commit()
		ThemeHelper.applyNightMode(context)
		assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode())
		prefs.edit().putString(ThemeHelper.PREF_THEME, "light").commit()
		ThemeHelper.applyNightMode(context)
		assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode())
		AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
	}

	@Test
	@Config(sdk = [31, 34, 36])
	fun dynamicColors_replaceAccentOnAndroid12() {
		val defaultAccent = accentOf(Robolectric.buildActivity(FolderListActivity::class.java).setup().get())
		prefs.edit().putBoolean(ThemeHelper.PREF_DYNAMIC_COLORS, true).commit()
		val dynamic = Robolectric.buildActivity(FolderListActivity::class.java).setup().get()

		assertEquals(dynamic.getColor(android.R.color.system_accent1_600), accentOf(dynamic))
		// The "New folder" button uses system accent 3
		val secondary = TypedValue()
		assertTrue(dynamic.theme.resolveAttribute(R.attr.fsColorSecondaryActionContainer, secondary, true))
		assertEquals(dynamic.getColor(android.R.color.system_accent3_100), secondary.data)
		assertNotEquals(defaultAccent, accentOf(dynamic))
	}

	@Test
	@Config(sdk = [28])
	fun dynamicColors_ignoredBeforeAndroid12() {
		prefs.edit().putBoolean(ThemeHelper.PREF_DYNAMIC_COLORS, true).commit()
		assertFalse(ThemeHelper.isDynamicColorEnabled(context))
		val activity = Robolectric.buildActivity(FolderListActivity::class.java).setup().get()
		assertEquals(activity.getColor(R.color.fs_accent), accentOf(activity))
	}

	@Test
	@Config(sdk = [28, 31, 36])
	fun blur_isNotEnabledWithoutXiaomiSupport() {
		BlurSupport.configure(context)
		assertFalse(BlurSupport.isSupported(context))
		assertFalse(MiuiSuperBlur.isSupportBlur())
	}

	@Test
	@Config(sdk = [23, 28, 36])
	fun miuixSwitchRow_reportsCheckedStateToAccessibility() {
		val themed = ContextThemeWrapper(context, R.style.Theme_FileSaver)
		val view = MiuixSwitchView(themed)
		view.setTitle("Register with media scanner")
		view.setChecked(true)

		val info = AccessibilityNodeInfo.obtain()
		view.onInitializeAccessibilityNodeInfo(info)
		assertTrue(info.isCheckable)
		assertTrue(info.isChecked)
		assertEquals(android.widget.Switch::class.java.name, info.className)
	}
}
