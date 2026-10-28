package link.standen.michael.phonesaver

import android.app.Application
import link.standen.michael.phonesaver.util.BlurSupport
import link.standen.michael.phonesaver.util.ThemeHelper

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
