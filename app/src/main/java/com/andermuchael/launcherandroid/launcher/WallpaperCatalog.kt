package com.andermuchael.launcherandroid.launcher

data class WallpaperOption(
    val name: String,
    val url: String,
    val credit: String
)

val cosmicWallpapers = listOf(
    WallpaperOption(
        "Orion Nebula",
        "https://raw.githubusercontent.com/pop-os/cosmic-wallpapers/master/original/orion_nebula_nasa_heic0601a.jpg",
        "NASA / ESA Hubble • COSMIC Wallpapers"
    ),
    WallpaperOption(
        "Otherworldly Earth",
        "https://raw.githubusercontent.com/pop-os/cosmic-wallpapers/master/original/otherworldly_earth_nasa_ISS064-E-29444.jpg",
        "NASA Earth Observatory • COSMIC Wallpapers"
    ),
    WallpaperOption(
        "Tarantula Nebula",
        "https://raw.githubusercontent.com/pop-os/cosmic-wallpapers/master/original/tarantula_nebula_nasa_PIA23646.jpg",
        "NASA / Webb • COSMIC Wallpapers"
    ),
    WallpaperOption(
        "Webb Inspired",
        "https://raw.githubusercontent.com/pop-os/cosmic-wallpapers/master/original/webb-inspired-wallpaper-system76.jpg",
        "System76 • CC BY 4.0"
    ),
    WallpaperOption(
        "COSMIC Desktop",
        "https://raw.githubusercontent.com/pop-os/wallpapers/master/original/kate-hazen-COSMIC-desktop-wallpaper.png",
        "Kate Hazen / System76 • CC BY-SA 4.0"
    ),
    WallpaperOption(
        "COSMIC Space",
        "https://raw.githubusercontent.com/pop-os/wallpapers/master/original/kate-hazen-pop-space.png",
        "Kate Hazen / System76 • CC BY-SA 4.0"
    )
)
