package me.zhanghai.android.files.filelist

import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max
import kotlin.math.min

/**
 * Long-press initiated range selection for list and grid layouts, including edge autoscrolling.
 * The caller owns stable file identities and merges the provisional range with prior selections.
 */
class FileDragSelectTouchListener(
    private val recyclerView: RecyclerView,
    private val onRange: (Int, Int) -> Unit
) : RecyclerView.SimpleOnItemTouchListener() {
    private var anchor = RecyclerView.NO_POSITION
    private var lastX = 0f
    private var lastY = 0f
    private var latest = RecyclerView.NO_POSITION

    private val scrollFrame = object : Runnable {
        override fun run() {
            if (anchor == RecyclerView.NO_POSITION) return
            val edge = max(48f * recyclerView.resources.displayMetrics.density,
                recyclerView.height * 0.125f)
            val direction = when {
                lastY < edge -> -1
                lastY > recyclerView.height - edge -> 1
                else -> 0
            }
            if (direction == 0 || !recyclerView.canScrollVertically(direction)) return
            val penetration = if (direction < 0) edge - lastY
                else lastY - (recyclerView.height - edge)
            val fraction = (penetration / edge).coerceIn(0f, 1f)
            recyclerView.scrollBy(0, ((4 + 30 * fraction * fraction).toInt() * direction))
            updateRange()
            recyclerView.postOnAnimation(this)
        }
    }

    fun start(position: Int) {
        stop()
        if (position !in 0 until (recyclerView.adapter?.itemCount ?: 0)) return
        anchor = position
        latest = position
        onRange(anchor, anchor)
    }

    fun stop() {
        anchor = RecyclerView.NO_POSITION
        latest = RecyclerView.NO_POSITION
        recyclerView.removeCallbacks(scrollFrame)
    }

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        if (anchor == RecyclerView.NO_POSITION) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> { track(e); return true }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { stop(); return true }
        }
        return false
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> track(e)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> stop()
        }
    }

    private fun track(e: MotionEvent) {
        lastX = e.x
        lastY = e.y
        updateRange()
        recyclerView.removeCallbacks(scrollFrame)
        recyclerView.postOnAnimation(scrollFrame)
    }

    private fun updateRange() {
        if (anchor == RecyclerView.NO_POSITION) return
        val count = recyclerView.adapter?.itemCount ?: 0
        if (count == 0) { stop(); return }
        val y = lastY.coerceIn(1f, (recyclerView.height - 1).coerceAtLeast(1).toFloat())
        var child = recyclerView.findChildViewUnder(lastX, y)
        if (child == null) {
            // Hit the nearest visible tile when crossing a grid gap or a clipped list row.
            var best = Float.MAX_VALUE
            for (i in 0 until recyclerView.childCount) {
                val visible = recyclerView.getChildAt(i)
                val dx = if (lastX < visible.left) visible.left - lastX
                    else if (lastX > visible.right) lastX - visible.right else 0f
                val dy = if (y < visible.top) visible.top - y
                    else if (y > visible.bottom) y - visible.bottom else 0f
                val distance = dx * dx + dy * dy
                if (distance < best) { best = distance; child = visible }
            }
        }
        val position = child?.let { recyclerView.getChildAdapterPosition(it) }
            ?: RecyclerView.NO_POSITION
        if (position in 0 until count && position != latest) {
            latest = position
            onRange(min(anchor, position), max(anchor, position))
        }
    }
}
