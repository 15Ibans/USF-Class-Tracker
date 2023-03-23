package me.ibans.usfclasstracker

import com.diogonunes.jcolor.Attribute
import com.gargoylesoftware.htmlunit.BrowserVersion
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException
import com.gargoylesoftware.htmlunit.ProxyConfig
import com.gargoylesoftware.htmlunit.WebClient
import com.gargoylesoftware.htmlunit.html.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.concurrent.fixedRateTimer

fun main(args: Array<String>) {
    val configFile = File("config.json")
    val gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    if (!configFile.exists()) {
        println("Config file does not exist. Creating then exiting...")
        val config = Config()
        val writer = FileWriter(configFile)
        gson.toJson(config, writer)

        writer.flush()
        writer.close()
        return
    }

    val config = gson.fromJson(FileReader(configFile), Config::class.java)

    val tracker = CourseTracker(config.courses, config.period, config.pingDiscord, config.discordWebhook,
        config.discordUsers, config.proxyHost, config.proxyPort)

    println("Class Tracker started")

    tracker.apply {
        logCourseInfo = true
        startTracking()
    }

}

enum class Term(val id: Int) {

    SPRING_2021(202101),
    SUMMER_2021(202105),
    FALL_2021(202108);

    companion object {
        fun from(id: Int): Term? = values().firstOrNull { it.id == id }
    }

}

// todo: make console output more pretty
//  use settings file instead of hardcoding stuff

class CourseTracker(
        private val courses: MutableList<Course>,
        private val period: Int,
        private val pingDiscord: Boolean,
        private val webhookUrl: String? = null,
        private val discordUsers: List<String>,
        private val proxyHost: String? = null,
        private val proxyPort: Int
) {

    private val scheduleSearchUrl = "https://usfweb.usf.edu/DSS/StaffScheduleSearch/"
    private val registrationURL = "https://usfonline.admin.usf.edu/pls/prod/bwskfreg.P_AltPin"

    private val logTime: String
        get() = "[${timeAsString()}]" colored Attribute.BRIGHT_BLACK_TEXT()

    var logCourseInfo = false

    init {
        Logger.getLogger("com.gargoylesoftware").level = Level.OFF
    }

    fun startTracking() {
        fixedRateTimer("tracker", false, 0L, period.toLong()) {
            try {
                WebClient(BrowserVersion.FIREFOX).use {
                    if (!proxyHost.isNullOrBlank() && proxyPort != -1) {
                        val proxyConfig = ProxyConfig(proxyHost, proxyPort)
                        it.options.proxyConfig = proxyConfig
                    }
                    for (course in courses.toTypedArray()) {
                        val success = getCourseInfo(course, it)
                        if (!success) {
                            println("$logTime Course retrieval returned nothing")
                            break
                        }
                    }
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                if (ex is FailingHttpStatusCodeException) {
                    println("$logTime Couldn't retrieve information due to HTTP status error")
                }
            }
        }
    }

    private fun timeAsString(): String {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    }

    private fun getCourseInfo(course: Course, webClient: WebClient): Boolean {
        val searchPage = webClient.getPage<HtmlPage>(scheduleSearchUrl)

//        val courseSubjectInput: HtmlTextInput = searchPage.getElementById("P_SUBJ") as HtmlTextInput
//        val courseNumberInput: HtmlTextInput = searchPage.getElementById("P_NUM") as HtmlTextInput

        val crnInput: HtmlTextInput = searchPage.getElementById("P_REF") as HtmlTextInput
        val semesterDropDown: HtmlSelect = searchPage.getElementById("P_SEMESTER") as HtmlSelect
        val submitButton: HtmlButton = searchPage.getByXPath<HtmlButton>("//button[@type='submit' and @value='Search']").first()

        crnInput.type(course.crn.toString())

        val term = Term.from(course.term) ?: throw Exception("Invalid term ID for CRN ${course.crn}: ${course.term}")

        semesterDropDown.apply {
            setSelectedAttribute<HtmlPage>(options.find { it.valueAttribute == term.id.toString() }, true)
        }

        val result = submitButton.click<HtmlPage>()

        val isHtml = result is HtmlPage

        if (isHtml) {
            val parsedData = parseHtml(result, course)

            if (!parsedData.isSuccessful) {
                println("$logTime Couldn't find $course")
                return true
            }
            if (parsedData.sections.isEmpty()) {
                return true
            }
            val sections = parsedData.sections
            if (logCourseInfo) {
                sections.forEach {
                    println("$logTime [${it.courseName}]" +
                            " ${it.crn} :: ${if(it.openSeats <= 0) "Closed" colored Attribute.RED_TEXT() else "Open" colored Attribute.BRIGHT_GREEN_TEXT()} ${it.openSeats}/${it.totalSeats}")
                }
            }
            if (pingDiscord && !webhookUrl.isNullOrBlank()) {
                sections.filter { it.openSeats > 0 }.forEach {
                    doWebhook(course, it, webhookUrl)
                }
            }
        }
        return isHtml
    }

    private fun parseHtml(page: HtmlPage, course: Course): ParsedData {
        val table: HtmlTable = page.getElementById("results") as? HtmlTable ?: return ParsedData(false, emptyList())
        val sections = mutableListOf<Section>()

        for (row in table.rows) {
            if (row.cells[3].asText() == course.crn.toString()) {
                val crn = row.cells[3].asText()
                val courseName = row.cells[4].asText()
                val openSeats = row.cells[12].asText().toInt()
                val enrolledSeats = row.cells[15].asText().toInt()
                if (openSeats + enrolledSeats != 0) {
                    sections.add(Section(courseName, crn, openSeats + enrolledSeats, openSeats))
                }
            }
        }

        return ParsedData(true, sections)
    }

    private fun doWebhook(course: Course, section: Section, url: String) {
        val webhook = DiscordWebhook(url)
        webhook.apply {
            setContent(discordUsers.joinToString(" ") { "<@!$it>" })
            addEmbed(EmbedObject()
                    .setTitle("Course Open!")
                    .setDescription("${section.courseName} is now open!")
                    .addField("CRN", section.crn, false)
                    .addField("Seats", "${section.openSeats}/${section.totalSeats}", false)
                    .addField("Add Course", registrationURL, false))
            execute()
        }
    }


    private fun getRepetitiveString(char: Char, length: Int): String {
        var str = ""
        for (i in 0 until length) {
            str += char
        }
        return str
    }

}

data class Course(val term: Int, val crn: Int)

data class Section(val courseName: String, val crn: String, val totalSeats: Int, val openSeats: Int)

data class ParsedData(val isSuccessful: Boolean, val sections: List<Section>)