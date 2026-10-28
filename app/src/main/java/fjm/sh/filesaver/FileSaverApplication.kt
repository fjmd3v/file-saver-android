package fjm.sh.filesaver

import android.app.Application
import fjm.sh.filesaver.util.BlurSupport
import fjm.sh.filesaver.util.ThemeHelper

/**
 * Application entry point. Applies the appearance settings before any activity is created.
 */
class FileSaverApplication : Application() {

	override fun onCreate() {
		super.onCreate()
		ThemeHelper.applyNightMode(this)
		BlurSupport.configure(this)
	}
}
