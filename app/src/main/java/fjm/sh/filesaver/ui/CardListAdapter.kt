package fjm.sh.filesaver.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.recyclerview.widget.RecyclerView
import com.hchen.himiuix.helper.ShadowHelper
import com.hchen.himiuix.widget.MiuixCardView
import fjm.sh.filesaver.R
import com.hchen.himiuix.R as MiuixR

/**
 * Items shown by [CardListAdapter].
 */
sealed class CardItem {

	/** A section title, rendered with the Miuix divider style. */
	data class Header(val title: CharSequence) : CardItem()

	/**
	 * A row inside a rounded group card. Consecutive rows form one group.
	 * @param key Opaque value passed back to click listeners.
	 */
	data class Row(
		val key: String,
		val title: CharSequence,
		val summary: CharSequence? = null,
		@DrawableRes val icon: Int = R.drawable.fs_ic_folder,
		val clickable: Boolean = true,
		val showChevron: Boolean = false,
		val actionIcon: Int = 0,
		val actionDescription: CharSequence? = null,
		val clickDescription: CharSequence? = null,
	) : CardItem()

	/** An informational card, used for empty states. */
	data class Message(
		@DrawableRes val icon: Int,
		val title: CharSequence,
		val body: CharSequence,
		val actionText: CharSequence? = null,
	) : CardItem()
}

/**
 * RecyclerView adapter rendering Miuix styled grouped cards.
 */
class CardListAdapter(
	private val onRowClick: (CardItem.Row, Int) -> Unit = { _, _ -> },
	private val onRowAction: (CardItem.Row, Int) -> Unit = { _, _ -> },
	private val onMessageAction: () -> Unit = {},
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

	companion object {
		private const val TYPE_HEADER = 0
		private const val TYPE_ROW = 1
		private const val TYPE_MESSAGE = 2
	}

	var items: List<CardItem> = emptyList()
		@SuppressLint("NotifyDataSetChanged")
		set(value) {
			field = value
			// Lists are short; group corner radii depend on neighbours, so rebind everything.
			notifyDataSetChanged()
		}

	override fun getItemCount(): Int = items.size

	override fun getItemViewType(position: Int): Int = when (items[position]) {
		is CardItem.Header -> TYPE_HEADER
		is CardItem.Row -> TYPE_ROW
		is CardItem.Message -> TYPE_MESSAGE
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		val inflater = LayoutInflater.from(parent.context)
		return when (viewType) {
			TYPE_HEADER -> HeaderHolder(inflater.inflate(R.layout.fs_item_header, parent, false))
			TYPE_MESSAGE -> MessageHolder(inflater.inflate(R.layout.fs_item_message, parent, false))
			else -> RowHolder(inflater.inflate(R.layout.fs_item_row, parent, false))
		}
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		when (val item = items[position]) {
			is CardItem.Header -> (holder as HeaderHolder).bind(item)
			is CardItem.Row -> (holder as RowHolder).bind(item, position)
			is CardItem.Message -> (holder as MessageHolder).bind(item)
		}
	}

	private inner class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
		private val title = view as TextView

		init {
			ViewCompat.setAccessibilityHeading(title, true)
		}

		fun bind(item: CardItem.Header) {
			title.text = item.title
		}
	}

	private inner class MessageHolder(view: View) : RecyclerView.ViewHolder(view) {
		private val icon = view.findViewById<ImageView>(R.id.message_icon)
		private val title = view.findViewById<TextView>(R.id.message_title)
		private val body = view.findViewById<TextView>(R.id.message_body)
		private val action = view.findViewById<Button>(R.id.message_action)

		init {
			ViewCompat.setAccessibilityHeading(title, true)
			action.setOnClickListener { onMessageAction() }
		}

		fun bind(item: CardItem.Message) {
			icon.setImageResource(item.icon)
			title.text = item.title
			body.text = item.body
			action.text = item.actionText
			action.visibility = if (item.actionText == null) View.GONE else View.VISIBLE
		}
	}

	private inner class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
		private val card = view as MiuixCardView
		private val row = view.findViewById<View>(R.id.row)
		private val icon = view.findViewById<ImageView>(R.id.icon)
		private val title = view.findViewById<TextView>(R.id.title)
		private val summary = view.findViewById<TextView>(R.id.summary)
		private val chevron = view.findViewById<View>(R.id.chevron)
		private val action = view.findViewById<ImageButton>(R.id.action)
		private val radius = view.resources.getDimension(MiuixR.dimen.miuix_prefs_card_radius)

		// Miuix press feedback (background darkens/lightens like Miuix preferences)
		private val shadowHelper = ShadowHelper.init(row)

		init {
			// Title and summary are read together as one item by screen readers
			ViewCompat.setScreenReaderFocusable(row, true)
			@SuppressLint("ClickableViewAccessibility")
			row.setOnTouchListener { _, event ->
				if (row.isClickable) shadowHelper.onTouchEvent(event)
				false
			}
			row.setOnClickListener {
				val position = bindingAdapterPosition
				(items.getOrNull(position) as? CardItem.Row)?.let { onRowClick(it, position) }
			}
			action.setOnClickListener {
				val position = bindingAdapterPosition
				(items.getOrNull(position) as? CardItem.Row)?.let { onRowAction(it, position) }
			}
		}

		fun bind(item: CardItem.Row, position: Int) {
			icon.setImageResource(item.icon)
			title.text = item.title
			summary.text = item.summary
			summary.visibility = if (item.summary.isNullOrEmpty()) View.GONE else View.VISIBLE
			chevron.visibility = if (item.showChevron) View.VISIBLE else View.GONE

			row.isClickable = item.clickable
			row.isFocusable = item.clickable
			shadowHelper.setEnabled(item.clickable)
			if (item.clickable && item.clickDescription != null) {
				// Announce what the row does, e.g. "Double tap to open DCIM"
				ViewCompat.replaceAccessibilityAction(row, AccessibilityActionCompat.ACTION_CLICK, item.clickDescription, null)
			}

			if (item.actionIcon != 0) {
				action.visibility = View.VISIBLE
				action.setImageResource(item.actionIcon)
				action.contentDescription = item.actionDescription
			} else {
				action.visibility = View.GONE
			}

			// Round the outer corners of each group of consecutive rows
			val first = items.getOrNull(position - 1) !is CardItem.Row
			val last = items.getOrNull(position + 1) !is CardItem.Row
			card.setTlRadius(if (first) radius else 0f).setTrRadius(if (first) radius else 0f)
				.setBlRadius(if (last) radius else 0f).setBrRadius(if (last) radius else 0f)
			card.invalidate()
			(card.layoutParams as? RecyclerView.LayoutParams)?.let {
				it.bottomMargin = if (last) card.resources.getDimensionPixelSize(MiuixR.dimen.miuix_basic_margin) else 0
				card.layoutParams = it
			}
		}
	}
}
