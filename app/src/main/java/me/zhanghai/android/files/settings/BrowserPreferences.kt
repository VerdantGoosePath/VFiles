package me.zhanghai.android.files.settings

/** Where the list/grid toggle is offered. Ordinals are persisted. */
enum class ViewTogglePlacement {
    HEADER,
    SHEET,
    BOTH;

    val inHeader: Boolean get() = this != SHEET
    val inSheet: Boolean get() = this != HEADER
}

/** How folder and file counts are presented above the list. Ordinals are persisted. */
enum class ItemCountStyle {
    ICON,
    TEXT,
    BOTH
}
