package me.zhanghai.android.files.filelist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.zhanghai.android.files.R

/** The host owns create/rename/confirmation contracts; no competing dialog implementations. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VFilesCreateMenu(
    enabled: Boolean,
    onCreateFile: () -> Unit,
    onCreateDirectory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled && expanded) { expanded = false }
    LaunchedEffect(enabled) { if (!enabled) expanded = false }
    if (!enabled) return
    Box(modifier = modifier) {
        FloatingActionButtonMenu(
            expanded = expanded,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            button = {
                ToggleFloatingActionButton(
                    checked = expanded,
                    onCheckedChange = { expanded = it },
                    modifier = Modifier.semantics {
                        contentDescription = if (expanded) "Close create menu" else "Create file or folder"
                    }
                ) {
                    Icon(
                        painter = painterResource(
                            if (expanded) R.drawable.close_icon_white_24dp
                            else R.drawable.add_icon_white_24dp
                        ),
                        contentDescription = null
                    )
                }
            }
        ) {
            FloatingActionButtonMenuItem(
                onClick = {
                    expanded = false
                    onCreateDirectory()
                },
                text = { Text("New folder") },
                icon = {
                    Icon(
                        painterResource(R.drawable.folder_icon_control_normal_24dp),
                        contentDescription = null
                    )
                }
            )
            FloatingActionButtonMenuItem(
                onClick = {
                    expanded = false
                    onCreateFile()
                },
                text = { Text("New file") },
                icon = {
                    Icon(
                        painterResource(R.drawable.file_icon_white_24dp),
                        contentDescription = null
                    )
                }
            )
        }
    }
}
