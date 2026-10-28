package com.example.model

/**
 * Aesthetic cover artwork styles for automatic album art generation.
 */
enum class CoverStyle(
    val title: String,
    val description: String,
    val primaryColorHex: Long,
    val secondaryColorHex: Long
) {
    NEON_SYNTHWAVE(
        title = "Neon Synthwave",
        description = "Vibrant magenta, cyan, and glowing soundwaves",
        primaryColorHex = 0xFFFF007F,
        secondaryColorHex = 0xFF00F0FF
    ),
    SUNSET_HORIZON(
        title = "Sunset Horizon",
        description = "Warm amber, crimson, and minimalist sun horizon",
        primaryColorHex = 0xFFFF5722,
        secondaryColorHex = 0xFFFFC107
    ),
    COBALT_NIGHT(
        title = "Cobalt Vinyl",
        description = "Deep obsidian navy and electric blue vinyl grooves",
        primaryColorHex = 0xFF1A237E,
        secondaryColorHex = 0xFF2979FF
    ),
    EMERALD_CHILL(
        title = "Emerald Chill",
        description = "Lush jade, mint gradients, and acoustic pulse",
        primaryColorHex = 0xFF004D40,
        secondaryColorHex = 0xFF00E676
    ),
    VINTAGE_RETRO(
        title = "Vintage Studio",
        description = "Analog tape aesthetic, warm bronze, and retro badge",
        primaryColorHex = 0xFF3E2723,
        secondaryColorHex = 0xFFFFB74D
    ),
    AURORA_DREAM(
        title = "Aurora Dream",
        description = "Cosmic violet, emerald glow, and stellar atmosphere",
        primaryColorHex = 0xFF4A148C,
        secondaryColorHex = 0xFF7C4DFF
    );

    companion object {
        fun fromIndex(index: Int): CoverStyle {
            val values = entries
            return values[index.mod(values.size)]
        }
    }
}
