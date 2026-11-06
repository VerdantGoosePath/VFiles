package me.zhanghai.android.files.theme.custom

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import me.zhanghai.android.files.R

// Mirrors the fixed ThemeColor palette, omitting its wallpaper-dependent entry.
enum class AppIconColor(
    @param:ColorRes val backgroundColorRes: Int,
    @param:DrawableRes val iconRes: Int
) {
    DEFAULT(R.color.color_primary_light, R.mipmap.launcher_icon),
    COLOR_PRIMARY(R.color.color_primary, R.mipmap.launcher_icon_color_primary),
    MATERIAL_RED(R.color.material_red, R.mipmap.launcher_icon_material_red),
    MATERIAL_PINK(R.color.material_pink, R.mipmap.launcher_icon_material_pink),
    MATERIAL_PURPLE(R.color.material_purple, R.mipmap.launcher_icon_material_purple),
    MATERIAL_DEEP_PURPLE(R.color.material_deep_purple, R.mipmap.launcher_icon_material_deep_purple),
    MATERIAL_INDIGO(R.color.material_indigo, R.mipmap.launcher_icon_material_indigo),
    MATERIAL_BLUE(R.color.material_blue, R.mipmap.launcher_icon_material_blue),
    MATERIAL_LIGHT_BLUE(R.color.material_light_blue, R.mipmap.launcher_icon_material_light_blue),
    MATERIAL_CYAN(R.color.material_cyan, R.mipmap.launcher_icon_material_cyan),
    MATERIAL_TEAL(R.color.material_teal, R.mipmap.launcher_icon_material_teal),
    MATERIAL_GREEN(R.color.material_green, R.mipmap.launcher_icon_material_green),
    MATERIAL_LIGHT_GREEN(R.color.material_light_green, R.mipmap.launcher_icon_material_light_green),
    MATERIAL_LIME(R.color.material_lime, R.mipmap.launcher_icon_material_lime),
    MATERIAL_YELLOW(R.color.material_yellow, R.mipmap.launcher_icon_material_yellow),
    MATERIAL_AMBER(R.color.material_amber, R.mipmap.launcher_icon_material_amber),
    MATERIAL_ORANGE(R.color.material_orange, R.mipmap.launcher_icon_material_orange),
    MATERIAL_DEEP_ORANGE(R.color.material_deep_orange, R.mipmap.launcher_icon_material_deep_orange),
    MATERIAL_BROWN(R.color.material_brown, R.mipmap.launcher_icon_material_brown),
    MATERIAL_GREY(R.color.material_grey, R.mipmap.launcher_icon_material_grey),
    MATERIAL_BLUE_GREY(R.color.material_blue_grey, R.mipmap.launcher_icon_material_blue_grey);

    val aliasClassName: String
        get() = "me.zhanghai.android.files.launcher.AppIcon_$name"
}
