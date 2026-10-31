/*
 * Copyright (c) 2024 Christians Martínez Alvarado
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.mardous.booming.ui.adapters.song

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.annotation.LayoutRes
import androidx.annotation.MenuRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.mardous.booming.R
import com.mardous.booming.coil.DEFAULT_SONG_IMAGE
import com.mardous.booming.core.model.action.SongClickBehavior
import com.mardous.booming.core.model.sort.SortKey
import com.mardous.booming.core.sort.SongSortMode
import com.mardous.booming.data.model.Song
import com.mardous.booming.extensions.isActivated
import com.mardous.booming.extensions.loadPaletteImage
import com.mardous.booming.extensions.media.asSectionName
import com.mardous.booming.extensions.media.displayArtistName
import com.mardous.booming.extensions.media.songInfo
import com.mardous.booming.extensions.resolveColor
import com.mardous.booming.extensions.resources.hide
import com.mardous.booming.extensions.showToast
import com.mardous.booming.extensions.utilities.buildInfoString
import com.mardous.booming.ui.ISongCallback
import com.mardous.booming.ui.component.base.AbsMultiSelectAdapter
import com.mardous.booming.ui.component.base.MediaEntryViewHolder
import com.mardous.booming.ui.component.menu.OnClickMenu
import com.mardous.booming.ui.screen.player.PlayerViewModel
import com.mardous.booming.util.Preferences
import me.zhanghai.android.fastscroll.PopupTextProvider
import org.koin.androidx.viewmodel.ext.android.getViewModel
import kotlin.math.abs

private const val SWIPE_MAX_FRACTION = 0.25f
private const val SWIPE_TRIGGER_FRACTION = 0.95f

@SuppressLint("NotifyDataSetChanged")
@Suppress("LeakingThis")
open class SongAdapter(
    protected val activity: FragmentActivity,
    dataSet: List<Song>,
    @LayoutRes protected val itemLayoutRes: Int = R.layout.item_list,
    protected val sortMode: SongSortMode? = null,
    protected val callback: ISongCallback? = null,
) : AbsMultiSelectAdapter<SongAdapter.ViewHolder, Song>(activity, R.menu.menu_media_selection), PopupTextProvider {

    open var dataSet: List<Song> = dataSet
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    private var swipeToPlayNextTouchHelper: ItemTouchHelper? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        swipeToPlayNextTouchHelper?.attachToRecyclerView(null)
        swipeToPlayNextTouchHelper = ItemTouchHelper(SwipeToPlayNextCallback()).apply {
            attachToRecyclerView(recyclerView)
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        swipeToPlayNextTouchHelper?.attachToRecyclerView(null)
        swipeToPlayNextTouchHelper = null
        super.onDetachedFromRecyclerView(recyclerView)
    }

    protected open fun createViewHolder(view: View, viewType: Int): ViewHolder {
        return ViewHolder(view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view: View = LayoutInflater.from(parent.context).inflate(itemLayoutRes, parent, false)
        return createViewHolder(view, viewType)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val song: Song = dataSet[position]
        val isChecked = isChecked(song)
        holder.isActivated = isChecked
        holder.menu?.isGone = isChecked
        holder.title?.text = getSongTitle(song)
        holder.text?.text = getSongText(song)
        // Check if imageContainer exists, so we can have a smooth transition without
        // CardView clipping, if it doesn't exist in current layout set transition name to image instead.
        if (holder.imageContainer != null) {
            holder.imageContainer.transitionName = song.id.toString()
        } else {
            holder.image?.transitionName = song.id.toString()
        }
        holder.loadPaletteImage(song, DEFAULT_SONG_IMAGE)
    }

    private fun getSongTitle(song: Song): String {
        return when (sortMode?.selectedKey) {
            SortKey.FileName -> song.fileName
            else -> song.title
        }
    }

    protected open fun getSongText(song: Song): String? {
        return when (sortMode?.selectedKey) {
            SortKey.Year -> if (song.year > 0) {
                buildInfoString(song.displayArtistName(), song.year.toString())
            } else {
                song.displayArtistName()
            }

            SortKey.Album -> buildInfoString(song.displayArtistName(), song.albumName)

            else -> song.songInfo()
        }
    }

    override fun getItemCount(): Int {
        return dataSet.size
    }

    override fun getItemId(position: Int): Long {
        return dataSet[position].id
    }

    override fun getIdentifier(position: Int): Song? {
        return dataSet[position]
    }

    override fun getName(item: Song): String? {
        return item.title
    }

    override fun onMultipleItemAction(menuItem: MenuItem, selection: List<Song>) {
        callback?.songsMenuItemClick(selection, menuItem)
    }

    override fun getPopupText(view: View, position: Int): CharSequence {
        val song = dataSet.getOrNull(position) ?: return ""
        return when (sortMode?.selectedKey) {
            SortKey.Album -> song.albumName.asSectionName(sortMode)
            SortKey.Artist -> song.displayArtistName().asSectionName(sortMode)
            SortKey.Name -> song.title.asSectionName(sortMode)
            SortKey.Year -> ""
            SortKey.FileName -> song.fileName.asSectionName(sortMode)
            else -> song.title.asSectionName(sortMode)
        }
    }

    open inner class ViewHolder(view: View) : MediaEntryViewHolder(view) {
        protected open val song: Song
            get() = dataSet[bindingAdapterPosition]

        @get:MenuRes
        protected open val songMenuRes: Int
            get() = R.menu.menu_item_song

        protected val sharedElements: Array<Pair<View, String>>?
            get() = if (image != null && image.isVisible) arrayOf(image to image.transitionName) else null

        protected val songClickBehavior: SongClickBehavior
            get() = Preferences.songClickAction

        @CallSuper
        protected open fun onPrepareSongMenu(menu: Menu) {
            menu.findItem(R.id.action_play)
                ?.isVisible = !songClickBehavior.isAbleToPlay || Preferences.playOptionAlwaysVisible
        }

        protected open fun onSongMenuItemClick(item: MenuItem): Boolean {
            if (item.itemId == R.id.action_play) {
                val playerViewModel = activity.getViewModel<PlayerViewModel>()
                val playOptionBehavior = Preferences.playOptionClickBehavior
                playerViewModel.openSongs(bindingAdapterPosition, dataSet, playOptionBehavior)
                return true
            }
            return callback?.songMenuItemClick(song, item, sharedElements) ?: false
        }

        override fun onClick(view: View) {
            if (isInQuickSelectMode) {
                toggleChecked(bindingAdapterPosition)
            } else {
                val songClickBehavior = Preferences.songClickAction
                val playerViewModel = activity.getViewModel<PlayerViewModel>()
                playerViewModel.openSongs(bindingAdapterPosition, dataSet, songClickBehavior)
                if (!songClickBehavior.isAbleToPlay) {
                    activity.showToast(R.string.added_title_to_playing_queue)
                }
            }
        }

        override fun onLongClick(view: View): Boolean {
            return toggleChecked(bindingAdapterPosition)
        }

        init {
            play?.hide()
            menu?.setOnClickListener(object : OnClickMenu() {
                override val popupMenuRes: Int
                    get() = songMenuRes

                override fun onPreparePopup(menu: Menu) {
                    onPrepareSongMenu(menu)
                }

                override fun onMenuItemClick(item: MenuItem): Boolean {
                    return onSongMenuItemClick(item)
                }
            })
        }
    }

    /**
     * Swipe a song towards the end of the list to add it to "Play Next".
     */
    private inner class SwipeToPlayNextCallback :
        ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.END) {

        private val density = activity.resources.displayMetrics.density
        private val iconMargin = (24 * density).toInt()
        private val iconSize = (28 * density).toInt()
        private val backgroundColor =
            activity.resolveColor(androidx.appcompat.R.attr.colorPrimary)
        private val iconColor =
            activity.resolveColor(com.google.android.material.R.attr.colorOnPrimary)
        private val backgroundPaint = Paint().apply { color = backgroundColor }
        private val playNextIcon = AppCompatResources
            .getDrawable(activity, R.drawable.ic_queue_music_24dp)
            ?.mutate()
            ?.apply { setTint(iconColor) }

        private var activeSwipeHolder: RecyclerView.ViewHolder? = null
        private var lastVisibleDx = 0f
        private var maxSwipeDistance = 0f
        private val recoveryStartDx = mutableMapOf<RecyclerView.ViewHolder, Float>()
        private val recoveryScales = mutableMapOf<RecyclerView.ViewHolder, Float>()

        /**
         * Swipe success is handled manually when the touch is released, so ItemTouchHelper must
         * always animate the item back instead of flinging it off the list.
         */
        override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float =
            Float.MAX_VALUE

        override fun getSwipeEscapeVelocity(defaultValue: Float): Float = Float.MAX_VALUE

        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            return if (Preferences.swipeToPlayNext && !isInQuickSelectMode) {
                super.getMovementFlags(recyclerView, viewHolder)
            } else {
                0
            }
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean = false

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            // Never reached: getSwipeThreshold() prevents ItemTouchHelper from completing a swipe.
        }

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            when {
                actionState == ItemTouchHelper.ACTION_STATE_SWIPE && viewHolder != null -> {
                    activeSwipeHolder = viewHolder
                    recoveryStartDx.remove(viewHolder)
                    recoveryScales.remove(viewHolder)
                    lastVisibleDx = 0f
                }

                actionState == ItemTouchHelper.ACTION_STATE_IDLE -> {
                    val holder = activeSwipeHolder ?: return
                    activeSwipeHolder = null
                    if (holder.itemView.parent == null) {
                        recoveryStartDx.remove(holder)
                        recoveryScales.remove(holder)
                        return
                    }
                    // Remember where the item was left so the return animation can start from
                    // the clamped distance instead of the raw finger distance.
                    recoveryStartDx[holder] = lastVisibleDx
                    if (maxSwipeDistance > 0f &&
                        abs(lastVisibleDx) >= maxSwipeDistance * SWIPE_TRIGGER_FRACTION
                    ) {
                        addToPlayNext(holder)
                    }
                }
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            recoveryStartDx.remove(viewHolder)
            recoveryScales.remove(viewHolder)
        }

        private fun addToPlayNext(viewHolder: RecyclerView.ViewHolder) {
            val position = viewHolder.bindingAdapterPosition
            val song = dataSet.getOrNull(position)?.takeUnless { it == Song.emptySong || it.id < 0 }
                ?: return
            activity.getViewModel<PlayerViewModel>().queueNext(song)
            activity.showToast(R.string.added_title_to_playing_queue)
        }

        override fun onChildDraw(
            canvas: Canvas,
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            dX: Float,
            dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean
        ) {
            val currentMaxSwipeDistance = recyclerView.width * SWIPE_MAX_FRACTION
            val clampedDx = if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                val visibleDx = if (!isCurrentlyActive) {
                    // Keep the return animation proportional to the distance the item was
                    // actually translated to while the finger was down.
                    val scale = recoveryScales.getOrPut(viewHolder) {
                        val startDx = recoveryStartDx.remove(viewHolder)
                        if (startDx != null && dX != 0f) startDx / dX else 1f
                    }
                    dX * scale
                } else {
                    dX
                }
                visibleDx.coerceIn(-currentMaxSwipeDistance, currentMaxSwipeDistance).also {
                    if (isCurrentlyActive) {
                        maxSwipeDistance = currentMaxSwipeDistance
                        lastVisibleDx = it
                    }
                }
            } else {
                dX
            }

            if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && clampedDx != 0f) {
                val itemView = viewHolder.itemView
                val background = if (clampedDx > 0f) {
                    Rect(
                        itemView.left,
                        itemView.top,
                        itemView.left + clampedDx.toInt(),
                        itemView.bottom
                    )
                } else {
                    Rect(
                        itemView.right + clampedDx.toInt(),
                        itemView.top,
                        itemView.right,
                        itemView.bottom
                    )
                }
                canvas.drawRect(background, backgroundPaint)

                playNextIcon?.let { icon ->
                    val iconLeft = if (clampedDx > 0f) {
                        itemView.left + iconMargin
                    } else {
                        itemView.right - iconMargin - iconSize
                    }
                    val iconTop = itemView.top + (itemView.height - iconSize) / 2
                    icon.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
                    icon.draw(canvas)
                }
            }
            super.onChildDraw(
                canvas,
                recyclerView,
                viewHolder,
                clampedDx,
                dY,
                actionState,
                isCurrentlyActive
            )
        }
    }

    init {
        setHasStableIds(true)
    }

}