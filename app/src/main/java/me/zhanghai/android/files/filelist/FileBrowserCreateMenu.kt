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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCreateFile: () -> Unit,
    onCreateDirectory: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled && expanded) { onExpandedChange(false) }
    if (!enabled) return
    Box(modifier = modifier) {
        FloatingActionButtonMenu(
            expanded = expanded,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            button = {
                ToggleFloatingActionButton(
                    checked = expanded,
                    onCheckedChange = onExpandedChange,
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
                    onExpandedChange(false)
                    onCreateDirectory()
                },
                text = { Text(stringResource(R.string.file_create_directory_title)) },
                icon = {
                    Icon(
                        painterResource(R.drawable.folder_icon_control_normal_24dp),
                        contentDescription = null
                    )
                }
            )
            FloatingActionButtonMenuItem(
                onClick = {
                    onExpandedChange(false)
                    onCreateFile()
                },
                text = { Text(stringResource(R.string.file_create_file_title)) },
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
