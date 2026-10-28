package fjm.sh.filesaver.util

import android.annotation.SuppressLint
import android.app.Activity
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.widget.TextView
import com.hchen.himiuix.dialog.MiuixAlertDialog
import fjm.sh.filesaver.R

/**
 * A helper class for managing dialog pop ups. Dialogs use the Miuix XML dialog component.
 */
object DialogHelper {

	/**
	 * Shows the support dialog
	 */
	@SuppressLint("InflateParams")
	fun showSupportDialog(activity: Activity) {
		val view = activity.layoutInflater.inflate(R.layout.support_dialog, null)

		// Linkify
		view.findViewById<TextView>(R.id.support_link).apply {
			Linkify.addLinks(this, Linkify.ALL)
			movementMethod = LinkMovementMethod.getInstance()
		}

		MiuixAlertDialog(activity)
			.setTitle(activity.getString(R.string.support_header))
			.setCustomView(view)
			.setPositiveButton(activity.getString(android.R.string.ok)) { dialog, _ ->
				dialog.cancel()
			}
			.show()
	}

	/**
	 * Shows a confirmation dialog with Cancel and a confirm button.
	 */
	fun showConfirmDialog(activity: Activity, title: CharSequence, message: CharSequence,
						  confirmText: CharSequence, onConfirm: () -> Unit) {
		MiuixAlertDialog(activity)
			.setTitle(title)
			.setMessage(message)
			.setNegativeButton(activity.getString(R.string.dialog_negative), null)
			.setPositiveButton(confirmText) { _, _ -> onConfirm() }
			.setHapticFeedbackEnabled(true)
			.show()
	}
}
