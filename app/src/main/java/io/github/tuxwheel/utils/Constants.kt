package io.github.tuxwheel.utils

object Constants {
    const val ACTION_LOGGING_SERVICE_TOGGLED = "io.github.tuxwheel.loggingServiceToggled"
    const val ACTION_PREFERENCE_RESET = "io.github.tuxwheel.preferenceReset"
    const val ACTION_ALARM_TRIGGERED = "io.github.tuxwheel.alarmTriggered"
    const val ACTION_WHEEL_MODEL_CHANGED = "io.github.tuxwheel.wheelModelChanged"
    const val NOTIFICATION_BUTTON_CONNECTION = "io.github.tuxwheel.notificationConnectionButton"
    const val NOTIFICATION_BUTTON_LOGGING = "io.github.tuxwheel.notificationLoggingButton"
    const val NOTIFICATION_BUTTON_BEEP = "io.github.tuxwheel.notificationBeepButton"
    const val NOTIFICATION_BUTTON_LIGHT = "io.github.tuxwheel.notificationLightButton"
    const val NOTIFICATION_CHANNEL_ID_NOTIFICATION = "io.github.tuxwheel.Channel_Notification"
    const val notificationChannelName = "Notify"
    const val notificationChannelDescription = "Default Notify"
    const val INTENT_EXTRA_LOGGING_FILE_LOCATION = "logging_file_location"
    const val INTENT_EXTRA_IS_RUNNING = "is_running"
    const val INTENT_EXTRA_ALARM_TYPE = "alarm_type"
    const val INTENT_EXTRA_ALARM_VALUE = "alarm_value"
    const val MAIN_NOTIFICATION_ID = 423411
    const val LOG_FOLDER_NAME = "TuxWheel"
    const val LEGACY_LOG_FOLDER_NAME = "WheelLog"
    const val LOG_FOLDER_OLD_NAME = "WheelLog Logs"

    enum class WHEEL_TYPE {
        Unknown, KINGSONG, GOTWAY, NINEBOT, NINEBOT_Z, INMOTION, INMOTION_V2, VETERAN
    }

    fun wheel_type_from_string(wt: String): WHEEL_TYPE {
        return when (wt.lowercase()) {
            "kingsong" -> WHEEL_TYPE.KINGSONG
            "gotway", "gw", "begode", "eb", "extremebull", "extreme bull", "extreme_bull" -> WHEEL_TYPE.GOTWAY
            "ninebot", "segway" -> WHEEL_TYPE.NINEBOT
            "ninebot_z", "ninebotz" -> WHEEL_TYPE.NINEBOT_Z
            "inmotion" -> WHEEL_TYPE.INMOTION
            "leaperkim", "veteran" -> WHEEL_TYPE.VETERAN
            else -> WHEEL_TYPE.Unknown
        }
    }

    enum class ALARM_TYPE(val value: Int) {
        SPEED1(1),
        SPEED2(2),
        SPEED3(3),
        CURRENT(4),
        TEMPERATURE(5),
        PWM(6),
        BATTERY(7),
        WHEEL(8);
    }
}