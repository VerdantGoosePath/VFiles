/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import me.zhanghai.android.files.R
import com.h6ah4i.android.widget.advrecyclerview.draggable.DraggableItemAdapter
import com.h6ah4i.android.widget.advrecyclerview.draggable.ItemDraggableRange
import com.h6ah4i.android.widget.advrecyclerview.utils.AbstractDraggableItemViewHolder
import me.zhanghai.android.files.compat.foregroundCompat
import me.zhanghai.android.files.compat.isTransformedTouchPointInViewCompat
import me.zhanghai.android.files.databinding.BookmarkDirectoryItemBinding
import me.zhanghai.android.files.filelist.toUserFriendlyString
import me.zhanghai.android.files.navigation.BookmarkDirectory
import me.zhanghai.android.files.ui.SimpleAdapter
import me.zhanghai.android.files.util.layoutInflater

class BookmarkDirectoryListAdapter(
    private val listener: Listener
) : SimpleAdapter<BookmarkDirectory, BookmarkDirectoryListAdapter.ViewHolder>(),
    DraggableItemAdapter<BookmarkDirectoryListAdapter.ViewHolder> {
    override val hasStableIds: Boolean
        get() = true

    override fun getItemId(position: Int): Long = getItem(position).id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            BookmarkDirectoryItemBinding.inflate(parent.context.layoutInflater, parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val bookmarkDirectory = getItem(position)
        val binding = holder.binding
        // Need to remove the ripple before it's drawn onto the bitmap for dragging.
        binding.root.foregroundCompat!!.mutate().setVisible(!holder.dragState.isActive, false)
        binding.root.setOnClickListener { listener.editBookmarkDirectory(bookmarkDirectory) }
        binding.nameText.text = bookmarkDirectory.name
        binding.pathText.text = bookmarkDirectory.path.toUserFriendlyString()
        binding.dragHandleView.apply {
            isFocusable = true
            isClickable = true
            contentDescription = context.getString(R.string.storage_reorder)
            setOnClickListener {
                val positionNow = holder.bindingAdapterPosition
                if (positionNow == RecyclerView.NO_POSITION) return@setOnClickListener
                PopupMenu(context, this).apply {
                    menu.add(0, MOVE_UP, 0, R.string.storage_move_up).isEnabled = positionNow > 0
                    menu.add(0, MOVE_DOWN, 1, R.string.storage_move_down)
                        .isEnabled = positionNow < itemCount - 1
                    setOnMenuItemClickListener { item ->
                        val latest = holder.bindingAdapterPosition
                        if (latest == RecyclerView.NO_POSITION) return@setOnMenuItemClickListener false
                        when (item.itemId) {
                            MOVE_UP -> if (latest > 0) {
                                listener.moveBookmarkDirectory(latest, latest - 1)
                                true
                            } else false
                            MOVE_DOWN -> if (latest < itemCount - 1) {
                                listener.moveBookmarkDirectory(latest, latest + 1)
                                true
                            } else false
                            else -> false
                        }
                    }
                    show()
                }
            }
        }
    }

    override fun onCheckCanStartDrag(holder: ViewHolder, position: Int, x: Int, y: Int): Boolean =
        (holder.binding.root as ViewGroup).isTransformedTouchPointInViewCompat(
            x.toFloat(), y.toFloat(), holder.binding.dragHandleView, null
        )

    override fun onGetItemDraggableRange(holder: ViewHolder, position: Int): ItemDraggableRange? =
        null

    override fun onCheckCanDrop(draggingPosition: Int, dropPosition: Int): Boolean = true

    override fun onItemDragStarted(position: Int) {
        notifyDataSetChanged()
    }

    override fun onItemDragFinished(fromPosition: Int, toPosition: Int, result: Boolean) {
        notifyDataSetChanged()
    }

    override fun onMoveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition) {
            return
        }
        listener.moveBookmarkDirectory(fromPosition, toPosition)
    }

    companion object {
        private const val MOVE_UP = 1
        private const val MOVE_DOWN = 2
    }

    class ViewHolder(val binding: BookmarkDirectoryItemBinding) : AbstractDraggableItemViewHolder(
        binding.root
    )

    interface Listener {
        fun editBookmarkDirectory(bookmarkDirectory: BookmarkDirectory)
        fun moveBookmarkDirectory(fromPosition: Int, toPosition: Int)
    }
}
