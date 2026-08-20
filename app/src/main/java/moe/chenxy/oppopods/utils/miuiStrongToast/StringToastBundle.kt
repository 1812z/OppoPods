import android.app.PendingIntent
import android.os.Bundle

class StringToastBundle private constructor() {
    class Builder {
        private var packageName: String? = null
        private var stringToastCategory: String? = null
        private var target: PendingIntent? = null
        private var param: String? = null
        private var islandParam: String? = null
        private var notifyId: String? = null
        private var duration: Long = 2500L
        private var level: Float = 0f
        private var rapidRate: Float = 0f
        private var charge: String? = null
        private var stringToastChargeFlag: Int = 0
        private var statusBarStrongToast: String? = "show_custom_strong_toast"

        fun setPackageName(packageName: String?) = apply { this.packageName = packageName }
        fun setStrongToastCategory(category: String) = apply { stringToastCategory = category }
        fun setTarget(target: PendingIntent?) = apply { this.target = target }
        fun setParam(param: String?) = apply { this.param = param }
        fun setIslandParam(param: String?) = apply { this.islandParam = param }
        fun setNotifyId(notifyId: String?) = apply { this.notifyId = notifyId }
        fun setDuration(duration: Long) = apply { this.duration = duration }
        fun setLevel(level: Float) = apply { this.level = level }
        fun setRapidRate(rapidRate: Float) = apply { this.rapidRate = rapidRate }
        fun setCharge(charge: String?) = apply { this.charge = charge }
        fun setStringToastChargeFlag(stringToastChargeFlag: Int) = apply { this.stringToastChargeFlag = stringToastChargeFlag }
        fun setStatusBarStrongToast(statusBarStrongToast: String?) = apply { this.statusBarStrongToast = statusBarStrongToast }

        fun onCreate(): Bundle {
            return Bundle().apply {
                putString("package_name", packageName)
                putString("strong_toast_category", stringToastCategory)
                putParcelable("target", target)
                putString("param", param)
                putString("island_param", islandParam)
                putString("notifyId", notifyId)
                putLong("duration", duration)
                putFloat("level", level)
                putFloat("rapid_rate", rapidRate)
                putString("charge", charge)
                putInt("string_toast_charge_flag", stringToastChargeFlag)
                putString("status_bar_strong_toast", statusBarStrongToast)
            }
        }
    }
}
