/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

// The properties dialog now presents native Compose PrimaryScrollableTabRow controls while
// preserving the existing ViewPager and provider-specific Fragment data surfaces. This
// FragmentPagerAdapter compatibility boundary is isolated here; changing it requires migrating
// the underlying ViewPager to ViewPager2, not just replacing this adapter class.
@file:Suppress("DEPRECATION")

package me.zhanghai.android.files.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter

class TabFragmentPagerAdapter(
    fragmentManager: FragmentManager,
    private vararg val tabs: Pair<CharSequence?, () -> Fragment>
) : FragmentPagerAdapter(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
    override fun getItem(position: Int): Fragment = tabs[position].second()

    override fun getCount(): Int = tabs.size

    override fun getPageTitle(position: Int): CharSequence? = tabs[position].first
}
