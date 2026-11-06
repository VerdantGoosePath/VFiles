package me.zhanghai.android.files.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compose.theme.VFilesTheme
import me.zhanghai.android.files.compose.theme.observeCompose
import me.zhanghai.android.files.filelist.FileListActivity
import me.zhanghai.android.files.filelist.toUserFriendlyString
import me.zhanghai.android.files.navigation.BookmarkDirectories
import me.zhanghai.android.files.navigation.BookmarkDirectory
import me.zhanghai.android.files.navigation.EditBookmarkDirectoryDialogActivity
import me.zhanghai.android.files.navigation.EditBookmarkDirectoryDialogFragment
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.launchSafe
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.startActivitySafe

/**
 * Bookmarks remain stored/reordered in BookmarkDirectories. Visible up/down actions are a
 * keyboard, TV and screen-reader accessible alternative to the old drag handle.
 * Editing/removing continues through the existing confirmation dialog and path picker.
 */
class BookmarkDirectoryListFragment : Fragment() {
    private val directoryPicker =
        registerForActivityResult(FileListActivity.OpenDirectoryContract()) { path ->
            if (path != null) BookmarkDirectories.add(BookmarkDirectory(null, path))
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            VFilesTheme {
                BookmarkSettingsScreen(
                    onNavigateUp = { requireActivity().finish() },
                    onAddBookmark = { directoryPicker.launchSafe(null, this@BookmarkDirectoryListFragment) },
                    onEditBookmark = { bookmark ->
                        startActivitySafe(
                            EditBookmarkDirectoryDialogActivity::class.createIntent()
                                .putArgs(EditBookmarkDirectoryDialogFragment.Args(bookmark))
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarkSettingsScreen(
    onNavigateUp: () -> Unit,
    onAddBookmark: () -> Unit,
    onEditBookmark: (BookmarkDirectory) -> Unit
) {
    val bookmarks by Settings.BOOKMARK_DIRECTORIES.observeCompose()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_bookmark_directory_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_expressive_back))
                    }
                },
                scrollBehavior = scroll
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddBookmark,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.settings_expressive_add_bookmark)) }
            )
        }
    ) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = insets.calculateTopPadding() + 16.dp,
                bottom = insets.calculateBottomPadding() + 110.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                    Text(
                        stringResource(R.string.settings_bookmark_directories_title),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
                            .semantics { heading() }
                    )
                    if (bookmarks.isEmpty()) {
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                stringResource(R.string.settings_bookmark_directory_list_empty),
                                modifier = Modifier.padding(24.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                            bookmarks.forEachIndexed { index, bookmark ->
                                SegmentedListItem(
                                    onClick = { onEditBookmark(bookmark) },
                                    shapes = ListItemDefaults.segmentedShapes(index, bookmarks.size),
                                    content = {
                                        Text(
                                            bookmark.name,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    },
                                    leadingContent = {
                                        Icon(
                                            painterResource(R.drawable.bookmark_icon_control_normal_24dp),
                                            contentDescription = null
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            bookmark.path.toUserFriendlyString(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 3, overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    trailingContent = {
                                        Column(
                                            horizontalAlignment = Alignment.End,
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            TextButton(
                                                enabled = index > 0,
                                                onClick = { BookmarkDirectories.move(index, index - 1) }
                                            ) { Text(stringResource(R.string.settings_expressive_move_up)) }
                                            TextButton(
                                                enabled = index < bookmarks.lastIndex,
                                                onClick = { BookmarkDirectories.move(index, index + 1) }
                                            ) { Text(stringResource(R.string.settings_expressive_move_down)) }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
