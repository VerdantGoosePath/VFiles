package me.zhanghai.android.files.settings

import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.livedata.observeAsState
import androidx.core.app.LocaleManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import java.nio.charset.Charset
import java.util.Locale
import me.zhanghai.android.files.BuildConfig
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compat.LocaleConfigCompat
import me.zhanghai.android.files.compose.theme.VFilesAppearance
import me.zhanghai.android.files.compose.theme.VFilesTheme
import me.zhanghai.android.files.compose.theme.observeCompose
import me.zhanghai.android.files.filejob.FileJobService
import me.zhanghai.android.files.filelist.OpenApkDefaultAction
import me.zhanghai.android.files.filelist.toUserFriendlyString
import me.zhanghai.android.files.navigation.StandardDirectoriesLiveData
import me.zhanghai.android.files.theme.custom.ThemeColor
import me.zhanghai.android.files.theme.custom.wallpaperAccentColor
import me.zhanghai.android.files.theme.night.NightMode
import me.zhanghai.android.files.util.toList
import me.zhanghai.android.files.util.valueCompat

/**
 * Settings use existing typed SettingLiveData exclusively. Neither a second SharedPreferences
 * writer nor a remembered replica of a persistent value is introduced. In particular the
 * ThemeColor/NightMode/TruncateAt enum order is never changed.
 */
internal data class ExpressiveSettingRow(
    val key: String,
    val title: String,
    val summary: String? = null,
    val value: String? = null,
    val checked: Boolean? = null,
    val iconRes: Int? = null,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

@Composable
internal fun ExpressiveSettingsSection(title: String, rows: List<ExpressiveSettingRow>) {
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp).semantics { heading() },
            style = MaterialTheme.typography.titleMediumEmphasized
        )
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            rows.forEachIndexed { index, row ->
                SegmentedListItem(
                    onClick = row.onClick,
                    enabled = row.enabled,
                    shapes = ListItemDefaults.segmentedShapes(index, rows.size),
                    modifier = if (row.checked != null) {
                        Modifier.semantics {
                            role = Role.Switch
                            toggleableState = ToggleableState(row.checked)
                        }
                    } else Modifier,
                    leadingContent = row.iconRes?.let { id ->
                        { Icon(painterResource(id), contentDescription = null, modifier = Modifier.size(24.dp)) }
                    },
                    content = { Text(row.title, style = MaterialTheme.typography.titleMedium) },
                    supportingContent = row.summary?.let { summary ->
                        {
                            Text(
                                summary, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    trailingContent = {
                        if (row.checked != null) {
                            // The row owns the click; exposing a second clickable switch double
                            // activates TalkBack/switch access and can undo a setting instantly.
                            Switch(
                                checked = row.checked,
                                onCheckedChange = null,
                                modifier = Modifier.clearAndSetSemantics { }
                            )
                        } else if (row.value != null) {
                            Text(
                                row.value, style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExpressiveSettingsScreen(
    onNavigateUp: () -> Unit,
    onOpenDefaultDirectory: () -> Unit,
    onOpenStorages: () -> Unit,
    onOpenStandardDirectories: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenDefaultApps: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSystemLocales: () -> Unit,
    onSetAppLocalesPre33: (LocaleListCompat) -> Unit
) {
    val context = LocalContext.current
    val color by Settings.THEME_COLOR.observeCompose()
    val night by Settings.NIGHT_MODE.observeCompose()
    val black by Settings.BLACK_NIGHT_MODE.observeCompose()
    val animation by Settings.FILE_LIST_ANIMATION.observeCompose()
    val edgeToEdge by Settings.EDGE_TO_EDGE.observeCompose()
    val ellipsis by Settings.FILE_NAME_ELLIPSIZE.observeCompose()
    val directory by Settings.FILE_LIST_DEFAULT_DIRECTORY.observeCompose()
    val storages by Settings.STORAGES.observeCompose()
    val bookmarks by Settings.BOOKMARK_DIRECTORIES.observeCompose()
    val standardDirectories by StandardDirectoriesLiveData.observeAsState(StandardDirectoriesLiveData.valueCompat)
    val root by Settings.ROOT_STRATEGY.observeCompose()
    val encoding by Settings.ARCHIVE_FILE_NAME_ENCODING.observeCompose()
    val apkAction by Settings.OPEN_APK_DEFAULT_ACTION.observeCompose()
    val showPdf by Settings.SHOW_PDF_THUMBNAIL_PRE_28.observeCompose()
    val remoteThumbs by Settings.READ_REMOTE_FILES_FOR_THUMBNAIL.observeCompose()

    var dialog by remember { mutableStateOf<String?>(null) }
    var confirmRoot by remember { mutableStateOf(false) }
    val nightNames = remember(context) { context.resources.getStringArray(R.array.settings_night_mode_entries).toList() }
    val ellipsisNames = remember(context) { context.resources.getStringArray(R.array.settings_file_name_ellipsize_entries).toList() }
    val rootNames = remember(context) { context.resources.getStringArray(R.array.settings_root_strategy_entries).toList() }
    val apkNames = remember(context) { context.resources.getStringArray(R.array.settings_open_apk_default_action_entries).toList() }
    val systemDark = isSystemInDarkTheme()
    val previewDark = when (night) {
        NightMode.ON -> true
        NightMode.OFF -> false
        else -> systemDark
    }
    val currentLocale = LocaleManagerCompat.getApplicationLocales(context).toList().firstOrNull()
    val localeSummary = currentLocale?.getDisplayName(currentLocale)
        ?: stringResource(R.string.system_default)

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_expressive_back)
                        )
                    }
                },
                scrollBehavior = scroll
            )
        }
    ) { insets ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = insets.calculateTopPadding() + 12.dp,
                bottom = insets.calculateBottomPadding() + 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "preview") {
                Box(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                    AppearancePreview(color, previewDark, black)
                }
            }
            item(key = "appearance") {
                Box(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                    ExpressiveSettingsSection(
                        stringResource(R.string.settings_expressive_appearance),
                        listOf(
                            ExpressiveSettingRow(
                                "locale", stringResource(R.string.settings_locale_title),
                                localeSummary, onClick = {
                                    if (Build.VERSION.SDK_INT >= 33) onOpenSystemLocales() else dialog = "locale"
                                }
                            ),
                            ExpressiveSettingRow(
                                "color", stringResource(R.string.settings_theme_color_title),
                                stringResource(R.string.settings_theme_color_summary),
                                value = if (color == ThemeColor.DYNAMIC)
                                    stringResource(R.string.color_picker_dynamic_color_label)
                                else color.name.replace('_', ' ').lowercase()
                                    .replaceFirstChar { it.titlecase() },
                                onClick = { dialog = "color" }
                            ),
                            ExpressiveSettingRow(
                                "night", stringResource(R.string.settings_night_mode_title),
                                value = nightNames.getOrElse(night.ordinal) { night.name },
                                onClick = { dialog = "night" }
                            ),
                            ExpressiveSettingRow(
                                "black", stringResource(R.string.settings_black_night_mode),
                                stringResource(R.string.settings_expressive_black_explanation),
                                checked = black, onClick = { Settings.BLACK_NIGHT_MODE.putValue(!black) }
                            )
                        )
                    )
                }
            }
            item(key = "browsing") {
                Box(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                    ExpressiveSettingsSection(
                        stringResource(R.string.settings_expressive_browsing),
                        buildList {
                            add(ExpressiveSettingRow(
                                "animation", stringResource(R.string.settings_file_list_animation_title),
                                checked = animation,
                                onClick = { Settings.FILE_LIST_ANIMATION.putValue(!animation) }
                            ))
                            add(ExpressiveSettingRow(
                                "ellipsis", stringResource(R.string.settings_file_name_ellipsize_title),
                                value = ellipsisNames.getOrElse(
                                    listOf(TextUtils.TruncateAt.START, TextUtils.TruncateAt.MIDDLE,
                                        TextUtils.TruncateAt.END, TextUtils.TruncateAt.MARQUEE).indexOf(ellipsis)
                                ) { ellipsis.name },
                                onClick = { dialog = "ellipsis" }
                            ))
                            // Target SDK 34 still allows this user choice. Hide it only once the
                            // build actually targets the platform's enforced edge-to-edge behavior.
                            if (!(Build.VERSION.SDK_INT >= 35 && context.applicationInfo.targetSdkVersion >= 35)) {
                                add(ExpressiveSettingRow(
                                    "edge", stringResource(R.string.settings_edge_to_edge_title),
                                    stringResource(R.string.settings_edge_to_edge_summary),
                                    checked = edgeToEdge,
                                    onClick = { Settings.EDGE_TO_EDGE.putValue(!edgeToEdge) }
                                ))
                            }
                        }
                    )
                }
            }
            item(key = "storage") {
                Box(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                    ExpressiveSettingsSection(
                        stringResource(R.string.settings_expressive_storage),
                        listOf(
                            ExpressiveSettingRow(
                                "folder", stringResource(R.string.settings_default_directory_title),
                                directory.toUserFriendlyString(), onClick = onOpenDefaultDirectory
                            ),
                            ExpressiveSettingRow(
                                "storages", stringResource(R.string.settings_storages_title),
                                storages.filter { it.isVisible }.joinToString { it.getName(context) }
                                    .ifBlank { context.getString(R.string.settings_storages_summary_empty) },
                                onClick = onOpenStorages
                            ),
                            ExpressiveSettingRow(
                                "standard", stringResource(R.string.settings_standard_directories_title),
                                standardDirectories.filter { it.isEnabled }
                                    .joinToString { it.getTitle(context) }
                                    .ifBlank { context.getString(R.string.settings_standard_directories_summary_empty) },
                                onClick = onOpenStandardDirectories
                            ),
                            ExpressiveSettingRow(
                                "bookmarks", stringResource(R.string.settings_bookmark_directories_title),
                                bookmarks.joinToString { it.name }
                                    .ifBlank { context.getString(R.string.settings_bookmark_directories_summary_empty) },
                                onClick = onOpenBookmarks
                            )
                        )
                    )
                }
            }
            item(key = "opening") {
                Box(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                    ExpressiveSettingsSection(
                        stringResource(R.string.settings_expressive_opening),
                        buildList {
                            add(ExpressiveSettingRow(
                                "apps", stringResource(R.string.settings_default_apps_title),
                                stringResource(R.string.settings_default_apps_summary), onClick = onOpenDefaultApps
                            ))
                            add(ExpressiveSettingRow(
                                "apk", stringResource(R.string.settings_open_apk_default_action_title),
                                value = apkNames.getOrElse(apkAction.ordinal) { apkAction.name },
                                onClick = { dialog = "apk" }
                            ))
                            add(ExpressiveSettingRow(
                                "encoding", stringResource(R.string.settings_archive_file_name_encoding_title),
                                value = encoding, onClick = { dialog = "encoding" }
                            ))
                            if (Build.VERSION.SDK_INT < 28) {
                                add(ExpressiveSettingRow(
                                    "pdf", stringResource(R.string.settings_show_pdf_thumbnail_pre_28_title),
                                    stringResource(R.string.settings_show_pdf_thumbnail_pre_28_summary),
                                    checked = showPdf, onClick = { Settings.SHOW_PDF_THUMBNAIL_PRE_28.putValue(!showPdf) }
                                ))
                            }
                            add(ExpressiveSettingRow(
                                "remote", stringResource(R.string.settings_read_remote_files_for_thumbnail_title),
                                checked = remoteThumbs,
                                onClick = { Settings.READ_REMOTE_FILES_FOR_THUMBNAIL.putValue(!remoteThumbs) }
                            ))
                        }
                    )
                }
            }
            item(key = "advanced") {
                Box(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                    ExpressiveSettingsSection(
                        stringResource(R.string.settings_expressive_advanced),
                        listOf(ExpressiveSettingRow(
                            "root", stringResource(R.string.settings_root_strategy_title),
                            value = rootNames.getOrElse(root.ordinal) { root.name },
                            onClick = {
                                confirmRoot = FileJobService.runningJobCount > 0
                                if (!confirmRoot) dialog = "root"
                            }
                        ))
                    )
                }
            }
            item(key = "about") {
                Box(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                    ExpressiveSettingsSection(
                        stringResource(R.string.about_title),
                        listOf(ExpressiveSettingRow(
                            "about", stringResource(R.string.about_title),
                            stringResource(R.string.app_version), onClick = onOpenAbout
                        ))
                    )
                }
            }
        }
    }

    if (confirmRoot) {
        val running = FileJobService.runningJobCount
        AlertDialog(
            onDismissRequest = { confirmRoot = false },
            title = { Text(stringResource(R.string.settings_root_strategy_title)) },
            text = {
                Text(context.resources.getQuantityString(
                    R.plurals.settings_root_strategy_message_format, running, running
                ))
            },
            confirmButton = {
                TextButton(onClick = { confirmRoot = false; dialog = "root" }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRoot = false }) {
                    Text(stringResource(R.string.maybe_later))
                }
            }
        )
    }

    when (dialog) {
        "night" -> ExpressiveChoiceDialog(
            stringResource(R.string.settings_night_mode_title),
            NightMode.entries.mapIndexed { index, mode -> mode to nightNames[index] },
            night, { Settings.NIGHT_MODE.putValue(it) }, { dialog = null }
        )
        "color" -> ExpressiveChoiceDialog(
            stringResource(R.string.settings_theme_color_title),
            buildList {
                if (Build.VERSION.SDK_INT >= 31) add(
                    ThemeColor.DYNAMIC to context.getString(R.string.color_picker_dynamic_color_label)
                )
                addAll(ThemeColor.entries.filter { it != ThemeColor.DYNAMIC }.map {
                    it to it.name.replace('_', ' ').lowercase().replaceFirstChar(Char::titlecase)
                })
            },
            color, { Settings.THEME_COLOR.putValue(it) }, { dialog = null },
            colorFor = { selected ->
                Color(if (selected == ThemeColor.DYNAMIC) {
                    context.wallpaperAccentColor() ?: ContextCompat.getColor(context, selected.resourceId)
                } else ContextCompat.getColor(context, selected.resourceId))
            }
        )
        "ellipsis" -> ExpressiveChoiceDialog(
            stringResource(R.string.settings_file_name_ellipsize_title),
            listOf(TextUtils.TruncateAt.START, TextUtils.TruncateAt.MIDDLE,
                TextUtils.TruncateAt.END, TextUtils.TruncateAt.MARQUEE)
                .mapIndexed { index, value -> value to ellipsisNames[index] },
            ellipsis, { Settings.FILE_NAME_ELLIPSIZE.putValue(it) }, { dialog = null }
        )
        "root" -> ExpressiveChoiceDialog(
            stringResource(R.string.settings_root_strategy_title),
            me.zhanghai.android.files.provider.root.RootStrategy.entries.mapIndexed { index, v ->
                v to rootNames[index]
            },
            root, { Settings.ROOT_STRATEGY.putValue(it) }, { dialog = null }
        )
        "apk" -> ExpressiveChoiceDialog(
            stringResource(R.string.settings_open_apk_default_action_title),
            OpenApkDefaultAction.entries.mapIndexed { index, v -> v to apkNames[index] },
            apkAction, { Settings.OPEN_APK_DEFAULT_ACTION.putValue(it) }, { dialog = null }
        )
        "encoding" -> ExpressiveChoiceDialog(
            stringResource(R.string.settings_archive_file_name_encoding_title),
            remember { Charset.availableCharsets().map { it.key to it.value.displayName() } },
            encoding, { Settings.ARCHIVE_FILE_NAME_ENCODING.putValue(it) }, { dialog = null }
        )
        "locale" -> {
            val options = remember(context) {
                val locales = LocaleConfigCompat(context).supportedLocales?.toList().orEmpty()
                    .sortedBy { it.toLanguageTag() }
                listOf("" to context.getString(R.string.system_default)) +
                    locales.map { it.toLanguageTag() to it.getDisplayName(it) }
            }
            ExpressiveChoiceDialog(
                stringResource(R.string.settings_locale_title), options,
                currentLocale?.toLanguageTag().orEmpty(),
                { value ->
                    val locales = if (value.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                        else LocaleListCompat.forLanguageTags(value)
                    onSetAppLocalesPre33(locales)
                }, { dialog = null }
            )
        }
    }
}

/** A small, real composition of the same scheme resolver and components as the app. */
@Composable
private fun AppearancePreview(color: ThemeColor, dark: Boolean, black: Boolean) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.settings_expressive_preview),
            modifier = Modifier.padding(start = 16.dp).semantics { heading() },
            style = MaterialTheme.typography.titleMediumEmphasized
        )
        VFilesTheme(appearance = VFilesAppearance(color = color, dark = dark, black = black)) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(32.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                stringResource(R.string.settings_expressive_preview_sample),
                                style = MaterialTheme.typography.titleMediumEmphasized,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (dark) stringResource(R.string.settings_expressive_dark)
                                else stringResource(R.string.settings_expressive_light),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(3) { index ->
                            val scheme = MaterialTheme.colorScheme
                            val background = when (index) {
                                0 -> scheme.primaryContainer
                                1 -> scheme.secondaryContainer
                                else -> scheme.tertiaryContainer
                            }
                            Box(
                                Modifier.weight(1f).height(32.dp)
                                    .clip(MaterialTheme.shapes.medium).background(background)
                            )
                        }
                    }
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.settings_expressive_preview_row),
                            Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/** Single selection owner for entire row, with one radio semantics tree. */
@Composable
internal fun <T> ExpressiveChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
    onDismiss: () -> Unit,
    colorFor: ((T) -> Color)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 440.dp)) {
                itemsIndexed(options) { _, option ->
                    val isSelected = selected == option.first
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable(role = Role.RadioButton) {
                                onSelected(option.first)
                                onDismiss()
                            }.padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (colorFor != null) {
                            Box(Modifier.size(28.dp).clip(CircleShape)
                                .background(colorFor(option.first)))
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(
                            option.second,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        RadioButton(
                            selected = isSelected, onClick = null,
                            modifier = Modifier.clearAndSetSemantics { }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}

