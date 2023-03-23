package me.ibans.usfclasstracker

class Config {

    val period: Int = 30
        get() = if (field <= 30) 30 * 1000 else field * 1000

    val proxyHost: String = ""
    val proxyPort: Int = -1
    val pingDiscord: Boolean = false
    val discordWebhook: String = ""
    val discordUsers = mutableListOf<String>()
    val courses = mutableListOf<Course>()

}

