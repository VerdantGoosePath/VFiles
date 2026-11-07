package me.zhanghai.android.files.compose.components

/** Breadcrumb order comes from the provider path trail, never parse '/' from user-facing text. */
data class BreadcrumbUi(val id: String, val label: String)
