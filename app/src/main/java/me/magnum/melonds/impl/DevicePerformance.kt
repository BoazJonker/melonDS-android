package me.magnum.melonds.impl

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File
import kotlin.math.ceil
import kotlin.math.min

/**
 * Rates how powerful the device is, to pick the internal resolution the game starts at with the automatic resolution setting.
 */
object DevicePerformance {

    /**
     * @param startScale Internal resolution the game starts at
     * @param maxScale Highest internal resolution the automatic setting may use
     */
    enum class Tier(val startScale: Int, val maxScale: Int) {
        // Starting scales leave headroom for the heaviest scenes, like the train rides: 4x already matches a 1080p screen held upright
        LOW(2, 2),
        MID(2, 3),
        HIGH(3, 4),
        FLAGSHIP(4, 5),
    }

    // Chip model names (Build.SOC_MODEL) by tier
    private val FLAGSHIP_CHIPS = listOf(
        "SM8635", // Snapdragon 8s Gen 3
        "SM8650", // Snapdragon 8 Gen 3
        "SM8735", // Snapdragon 8s Gen 4
        "SM8750", // Snapdragon 8 Elite
        "SM8850", // Snapdragon 8 Elite Gen 5
        "MT6989", // Dimensity 9300
        "MT6991", // Dimensity 9400
    )
    private val HIGH_CHIP_PREFIXES = listOf("SM84", "SM85", "SM86", "SM87", "SM88", "MT698", "MT699", "TENSOR", "GS", "ZUMA", "EXYNOS 2", "S5E99")
    private val MID_CHIP_PREFIXES = listOf("SM7", "SM6", "MT687", "MT688", "MT689", "S5E88", "S5E89")

    @Volatile
    private var cachedTier: Tier? = null

    fun getTier(context: Context): Tier {
        return cachedTier ?: detectTier(context).also { cachedTier = it }
    }

    /**
     * Highest internal resolution worth using with the automatic setting: the tier's limit, and no more than the screen can show.
     */
    fun getMaxUsefulScale(context: Context): Int {
        val metrics = context.resources.displayMetrics
        val shortSide = min(metrics.widthPixels, metrics.heightPixels)
        // In landscape, the DS screen (192 pixels tall) fills the screen's short side. More pixels than that are not visible
        val screenLimit = ceil(shortSide / 192f).toInt().coerceAtLeast(2)
        return min(getTier(context).maxScale, screenLimit)
    }

    private fun detectTier(context: Context): Tier {
        val chip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.uppercase() else ""
        val tierFromChip = when {
            chip.isEmpty() || chip == Build.UNKNOWN.uppercase() -> null
            FLAGSHIP_CHIPS.any { chip.startsWith(it) } -> Tier.FLAGSHIP
            HIGH_CHIP_PREFIXES.any { chip.startsWith(it) } -> Tier.HIGH
            MID_CHIP_PREFIXES.any { chip.startsWith(it) } -> Tier.MID
            else -> null
        }

        val memoryGb = getTotalMemoryGb(context)
        val maxCpuGhz = getMaxCpuFrequencyGhz()
        val tierFromSpecs = when {
            memoryGb >= 11f && maxCpuGhz >= 3.0f -> Tier.FLAGSHIP
            memoryGb >= 7.5f && maxCpuGhz >= 2.8f -> Tier.HIGH
            memoryGb >= 5.5f || maxCpuGhz >= 2.4f -> Tier.MID
            else -> Tier.LOW
        }

        // A known chip is the best indication, but a device with little memory is held back to its specs tier
        return when {
            tierFromChip == null -> tierFromSpecs
            memoryGb in 0.1f..3.5f -> minOf(tierFromChip, Tier.LOW)
            else -> tierFromChip
        }
    }

    private fun getTotalMemoryGb(context: Context): Float {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0f
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.totalMem / (1024f * 1024f * 1024f)
    }

    private fun getMaxCpuFrequencyGhz(): Float {
        val maxKhz = (0 until Runtime.getRuntime().availableProcessors()).maxOfOrNull { cpu ->
            runCatching { File("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq").readText().trim().toLong() }.getOrDefault(0L)
        } ?: 0L
        return maxKhz / 1_000_000f
    }
}
