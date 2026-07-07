package ca.voiditswarranty.roadtripradar.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.data.Waypoint

private data class RouteEditorRowItem(
    val waypoint: Waypoint,
    val index: Int,
    val isActive: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteEditorSheet(
    visible: Boolean,
    waypoints: List<Waypoint>,
    activeWaypointId: String?,
    onSetActive: (String) -> Unit,
    onRemove: (String) -> Unit,
    onMoveCommit: (Int, Int) -> Unit,
    onClearRoute: () -> Unit,
    onClose: () -> Unit,
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showClearConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val unnamedTemplate = stringResource(R.string.waypoint_unnamed)
    val rowItems = waypoints.mapIndexed { idx, wp ->
        RouteEditorRowItem(wp, idx, isActive = wp.id == activeWaypointId)
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                stringResource(R.string.route_editor_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )

            AndroidView(
                factory = { ctx ->
                    val rv = RecyclerView(ctx).apply {
                        layoutManager = LinearLayoutManager(ctx)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                    }
                    val adapter = RouteEditorAdapter(
                        unnamedTemplate = unnamedTemplate,
                        onSetActive = onSetActive,
                        onRemove = onRemove,
                        onMoveCommit = onMoveCommit,
                    )
                    rv.adapter = adapter
                    ItemTouchHelper(adapter.itemTouchCallback).attachToRecyclerView(rv)
                    rv
                },
                update = { rv ->
                    val adapter = rv.adapter as? RouteEditorAdapter ?: return@AndroidView
                    adapter.submit(rowItems)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
            )

            Spacer(Modifier.height(12.dp))

            FilledTonalButton(
                onClick = { showClearConfirm = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_clear_target),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    onClearRoute()
                    onClose()
                }) { Text(stringResource(R.string.action_clear_target)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            title = { Text(stringResource(R.string.action_clear_target)) },
            text = { Text(stringResource(R.string.action_clear_route_confirm)) },
        )
    }
}

private class RouteEditorAdapter(
    private val unnamedTemplate: String,
    private val onSetActive: (String) -> Unit,
    private val onRemove: (String) -> Unit,
    private val onMoveCommit: (Int, Int) -> Unit,
) : RecyclerView.Adapter<RouteEditorAdapter.VH>() {

    private val items = mutableListOf<RouteEditorRowItem>()
    private var dragInProgress = false
    private var dragStartIndex = -1
    private var dragEndIndex = -1

    fun submit(newItems: List<RouteEditorRowItem>) {
        if (dragInProgress) return
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val context = parent.context
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8))
        }
        val drag = TextView(context).apply {
            text = "≡"
            textSize = 22f
            setPadding(dp(context, 12), 0, dp(context, 12), 0)
            contentDescription = context.getString(R.string.cd_drag_handle)
        }
        val index = TextView(context).apply {
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            minWidth = dp(context, 28)
            gravity = Gravity.CENTER
        }
        val center = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f,
            )
            setPadding(dp(context, 8), 0, dp(context, 8), 0)
        }
        val nameView = TextView(context).apply {
            textSize = 16f
        }
        val subtitleView = TextView(context).apply {
            textSize = 12f
            setTextColor(0xFF888888.toInt())
        }
        center.addView(nameView)
        center.addView(subtitleView)

        val activeBtn = TextView(context).apply {
            textSize = 14f
            setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8))
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(R.string.cd_set_active)
        }
        val removeBtn = TextView(context).apply {
            text = "✕"
            textSize = 18f
            setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8))
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(R.string.cd_remove_waypoint)
        }

        row.addView(drag)
        row.addView(index)
        row.addView(center)
        row.addView(activeBtn)
        row.addView(removeBtn)

        return VH(row, index, nameView, subtitleView, activeBtn, removeBtn)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val wp = item.waypoint
        holder.indexView.text = "${item.index + 1}."
        holder.nameView.text = wp.name?.takeIf { it.isNotBlank() }
            ?: unnamedTemplate.format(item.index + 1)
        holder.subtitleView.apply {
            val sub = wp.subtitle?.takeIf { it.isNotBlank() }
            if (sub != null) {
                text = sub
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }
        }
        holder.activeView.text = if (item.isActive) "● Active" else "○ Set active"
        holder.activeView.setOnClickListener {
            if (!item.isActive) onSetActive(wp.id)
        }
        holder.removeView.setOnClickListener {
            onRemove(wp.id)
        }
    }

    inner class VH(
        itemView: View,
        val indexView: TextView,
        val nameView: TextView,
        val subtitleView: TextView,
        val activeView: TextView,
        val removeView: TextView,
    ) : RecyclerView.ViewHolder(itemView)

    val itemTouchCallback = object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN,
        0,
    ) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
        ): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from < 0 || to < 0 || from >= items.size || to >= items.size) return false
            if (!dragInProgress) {
                dragInProgress = true
                dragStartIndex = from
            }
            dragEndIndex = to
            val moved = items.removeAt(from)
            items.add(to, moved)
            notifyItemMoved(from, to)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            if (dragInProgress) {
                if (dragStartIndex != dragEndIndex && dragStartIndex >= 0 && dragEndIndex >= 0) {
                    onMoveCommit(dragStartIndex, dragEndIndex)
                }
                dragInProgress = false
                dragStartIndex = -1
                dragEndIndex = -1
            }
        }
    }
}

private fun dp(context: Context, value: Int): Int =
    (value * context.resources.displayMetrics.density).toInt()
