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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.fragment.app.Fragment
import java.util.Locale
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compose.theme.VFilesTheme
import me.zhanghai.android.files.compose.theme.observeCompose
import me.zhanghai.android.files.filelist.CrossAppDragHold

class CrossAppDragSettingsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            VFilesTheme {
                CrossAppDragSettingsScreen(onNavigateUp = { requireActivity().finish() })
            }
        }
    }
}

@Composable
private fun currentLocale(): Locale =
    ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()

@Composable
internal fun formatHoldDuration(millis: Int): String = stringResource(
    R.string.settings_cross_app_drag_seconds_format,
    CrossAppDragHold.formatSeconds(millis, currentLocale())
)

/**
 * The main switch stands alone at the top; everything below only takes effect, and only accepts
 * input, while it is on. The hold duration snaps to half seconds on the slider, while the value
 * button opens an exact entry in tenths of a second.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CrossAppDragSettingsScreen(onNavigateUp: () -> Unit) {
    val enabled by Settings.FILE_LIST_CROSS_APP_DRAG.observeCompose()
    val storedHold by Settings.FILE_LIST_CROSS_APP_DRAG_HOLD_MILLIS.observeCompose()
    val holdMillis = CrossAppDragHold.coerce(storedHold)
    val allowDirectories by Settings.FILE_LIST_CROSS_APP_DRAG_ALLOW_DIRECTORIES.observeCompose()
    val allowMultiple by Settings.FILE_LIST_CROSS_APP_DRAG_ALLOW_MULTIPLE.observeCompose()
    var editing by rememberSaveable { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_cross_app_drag_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(painterResource(R.drawable.settings_expressive_back_24dp),
                            contentDescription = stringResource(R.string.settings_expressive_back))
                    }
                },
                scrollBehavior = scroll
            )
        }
    ) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = insets.calculateTopPadding() + 16.dp,
                bottom = insets.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item(key = "main") {
                Box(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MainSwitch(
                            title = stringResource(R.string.settings_cross_app_drag_main_switch),
                            checked = enabled,
                            onCheckedChange = { Settings.FILE_LIST_CROSS_APP_DRAG.putValue(it) }
                        )
                        Text(
                            stringResource(R.string.settings_cross_app_drag_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
            item(key = "gesture") {
                Box(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeading(stringResource(R.string.settings_cross_app_drag_gesture), enabled)
                        HoldDurationCard(
                            holdMillis = holdMillis,
                            enabled = enabled,
                            onHoldChange = { millis ->
                                if (millis != storedHold) {
                                    Settings.FILE_LIST_CROSS_APP_DRAG_HOLD_MILLIS.putValue(millis)
                                }
                            },
                            onEdit = { editing = true }
                        )
                    }
                }
            }
            item(key = "items") {
                Box(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ExpressiveSettingsSection(
                            stringResource(R.string.settings_cross_app_drag_items),
                            listOf(
                                ExpressiveSettingRow(
                                    "folders",
                                    stringResource(R.string.settings_cross_app_drag_folders_title),
                                    stringResource(R.string.settings_cross_app_drag_folders_summary),
                                    checked = allowDirectories,
                                    iconRes = R.drawable.directory_icon_white_24dp,
                                    enabled = enabled,
                                    onClick = {
                                        Settings.FILE_LIST_CROSS_APP_DRAG_ALLOW_DIRECTORIES
                                            .putValue(!allowDirectories)
                                    }
                                ),
                                ExpressiveSettingRow(
                                    "multiple",
                                    stringResource(R.string.settings_cross_app_drag_multiple_title),
                                    stringResource(R.string.settings_cross_app_drag_multiple_summary),
                                    checked = allowMultiple,
                                    iconRes = R.drawable.select_all_icon_control_normal_24dp,
                                    enabled = enabled,
                                    onClick = {
                                        Settings.FILE_LIST_CROSS_APP_DRAG_ALLOW_MULTIPLE
                                            .putValue(!allowMultiple)
                                    }
                                )
                            ),
                            enabled = enabled
                        )
                        Column(
                            Modifier.padding(horizontal = 16.dp)
                                .alpha(if (enabled) 1f else DISABLED_CONTENT_ALPHA),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                stringResource(allowedSummary(allowDirectories, allowMultiple)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                stringResource(R.string.settings_cross_app_drag_archive_note),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
    if (editing) {
        HoldDurationDialog(
            holdMillis = holdMillis,
            onConfirm = { millis ->
                Settings.FILE_LIST_CROSS_APP_DRAG_HOLD_MILLIS.putValue(millis)
                editing = false
            },
            onDismiss = { editing = false }
        )
    }
    // The dialog can outlive the switch being turned off elsewhere; never edit while off.
    LaunchedEffect(enabled) { if (!enabled) editing = false }
}

internal fun allowedSummary(allowDirectories: Boolean, allowMultiple: Boolean): Int = when {
    allowDirectories && allowMultiple -> R.string.settings_cross_app_drag_allowed_all
    allowDirectories -> R.string.settings_cross_app_drag_allowed_file_or_folder
    allowMultiple -> R.string.settings_cross_app_drag_allowed_files_multiple
    else -> R.string.settings_cross_app_drag_allowed_files
}

@Composable
private fun SectionHeading(title: String, enabled: Boolean) {
    Text(
        title,
        modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
            .alpha(if (enabled) 1f else DISABLED_CONTENT_ALPHA)
            .semantics { heading() },
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary
    )
}

/** A prominent, tonal on/off bar in the style of the platform's main switch preference. */
@Composable
private fun MainSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = if (checked) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (checked) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .heightIn(min = 72.dp)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            // The row owns the toggle; a second clickable switch would double activate.
            Switch(checked = checked, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics { })
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HoldDurationCard(
    holdMillis: Int,
    enabled: Boolean,
    onHoldChange: (Int) -> Unit,
    onEdit: () -> Unit
) {
    val value = formatHoldDuration(holdMillis)
    val editLabel = stringResource(R.string.settings_cross_app_drag_hold_edit)
    val contentAlpha = if (enabled) 1f else DISABLED_CONTENT_ALPHA
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(Modifier.alpha(contentAlpha)) {
                    SettingsRowIcon(R.drawable.settings_icon_pan_tool)
                }
                Column(Modifier.weight(1f).alpha(contentAlpha)) {
                    Text(
                        stringResource(R.string.settings_cross_app_drag_hold_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        stringResource(R.string.settings_cross_app_drag_hold_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = onEdit,
                    enabled = enabled,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.semantics { contentDescription = "$editLabel, $value" }
                ) {
                    Text(value, style = MaterialTheme.typography.labelLarge)
                }
            }
            // No platform steps: those would also snap an exact entry such as 1.3 s on display.
            val sliderState = remember {
                SliderState(
                    holdMillis.toFloat(),
                    0,
                    CrossAppDragHold.MIN_MILLIS.toFloat()..CrossAppDragHold.MAX_MILLIS.toFloat()
                )
            }
            LaunchedEffect(holdMillis) { sliderState.value = holdMillis.toFloat() }
            Slider(
                state = sliderState,
                onValueChange = {
                    val snapped = CrossAppDragHold.snapToSlider(it)
                    sliderState.value = snapped.toFloat()
                    onHoldChange(snapped)
                },
                enabled = enabled,
                modifier = Modifier.padding(top = 8.dp).semantics {
                    stateDescription = value
                }
            )
            Row(Modifier.fillMaxWidth().alpha(contentAlpha).clearAndSetSemantics { }) {
                Text(
                    formatHoldDuration(CrossAppDragHold.MIN_MILLIS),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    formatHoldDuration(CrossAppDragHold.MAX_MILLIS),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HoldDurationDialog(
    holdMillis: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val initial = CrossAppDragHold.formatSeconds(holdMillis, currentLocale())
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length)))
    }
    val parsed = CrossAppDragHold.parseSeconds(field.text)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_cross_app_drag_hold_title)) },
        text = {
            OutlinedTextField(
                value = field,
                onValueChange = { if (CrossAppDragHold.isAcceptableInput(it.text)) field = it },
                label = { Text(stringResource(R.string.settings_cross_app_drag_hold_field)) },
                suffix = { Text("s") },
                singleLine = true,
                isError = field.text.isNotEmpty() && parsed == null,
                supportingText = {
                    Text(stringResource(
                        R.string.settings_cross_app_drag_hold_range,
                        formatHoldDuration(CrossAppDragHold.MIN_MILLIS),
                        formatHoldDuration(CrossAppDragHold.MAX_MILLIS)
                    ))
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { parsed?.let(onConfirm) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onConfirm) }, enabled = parsed != null) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}
