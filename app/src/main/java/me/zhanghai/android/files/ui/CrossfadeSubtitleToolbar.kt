/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */
package me.zhanghai.android.files.ui

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.appcompat.widget.Toolbar

/**
 * XML compatibility wrapper for the file-list action bar. The old implementation used
 * reflection to animate a private AndroidX Toolbar field on each subtitle update. That is
 * brittle across library versions, ignores the application's Compose MotionScheme and
 * introduces distracting animation when directory counts update rapidly. Native Toolbar
 * text updates provide the correct, immediate reduced-motion behavior.
 */
class CrossfadeSubtitleToolbar : Toolbar {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int) :
        super(context, attrs, defStyleAttr)
}
