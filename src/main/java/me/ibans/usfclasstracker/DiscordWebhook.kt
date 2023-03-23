package me.ibans.usfclasstracker

import com.google.gson.Gson
import java.awt.Color
import java.io.IOException
import java.lang.reflect.Array
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.*
import javax.net.ssl.HttpsURLConnection
import kotlin.collections.HashMap
import kotlin.collections.set


/**
 * Class used to execute Discord Webhooks with low effort
 *
 * Kotlinified version of https://gist.github.com/k3kdude/fba6f6b37594eae3d6f9475330733bdb
 */
@Suppress("unused")
class DiscordWebhook(private val url: String) {

    private var content: String? = null
    private var username: String? = null
    private var avatarUrl: String? = null
    private var tts = false
    private val embeds: MutableList<EmbedObject> = ArrayList()

    fun setContent(content: String?) = apply { this.content = content }

    fun setUsername(username: String?) = apply { this.username = username }

    fun setAvatarUrl(avatarUrl: String?) = apply { this.avatarUrl = avatarUrl }

    fun setTts(tts: Boolean) = apply { this.tts = tts }

    fun addEmbed(embed: EmbedObject) = apply { embeds.add(embed) }

    @Throws(IOException::class)
    fun execute() {
        if (content == null && embeds.isEmpty()) {
            throw IllegalArgumentException("Set content or add at least one EmbedObject")
        }
        val json = Gson().toJson(this)
        val url = URL(url)
        val connection = url.openConnection() as HttpsURLConnection
        connection.addRequestProperty("Content-Type", "application/json")
        connection.addRequestProperty("User-Agent", "Java-DiscordWebhook-BY-Gelox_")
        connection.doOutput = true
        connection.requestMethod = "POST"
        val stream = connection.outputStream
        stream.write(json.toString().toByteArray(StandardCharsets.UTF_8))
        stream.flush()
        stream.close()
        connection.inputStream.close() //I'm not sure why but it doesn't work without getting the InputStream
        connection.disconnect()
    }
}

@Suppress("unused")
class EmbedObject {
    var title: String? = null
    var description: String? = null
    var url: String? = null
    var color: Color? = null
    var footer: Footer? = null
    var thumbnail: Thumbnail? = null
    var image: Image? = null
    var author: Author? = null
    val fields: MutableList<Field> = ArrayList()

    fun setTitle(title: String?): EmbedObject {
        this.title = title
        return this
    }

    fun setDescription(description: String?): EmbedObject {
        this.description = description
        return this
    }

    fun setUrl(url: String): EmbedObject {
        this.url = url
        return this
    }

    fun setColor(color: String): EmbedObject {
        fun String.toColor(): Color? {
            if (!this.matches("[#][0-9a-fA-F]{6}".toRegex())) return null
            val digits: String = this.substring(1, this.length.coerceAtMost(7))
            val hxstr = "0x$digits"
            return Color.decode(hxstr)
        }
        this.color = color.toColor()
        return this
    }

    fun setColor(color: Color?): EmbedObject {
        this.color = color
        return this
    }

    fun setFooter(text: String, icon: String): EmbedObject {
        footer = Footer(text, icon)
        return this
    }

    fun setThumbnail(url: String): EmbedObject {
        thumbnail = Thumbnail(url)
        return this
    }

    fun setImage(url: String): EmbedObject {
        image = Image(url)
        return this
    }

    fun setAuthor(name: String, url: String, icon: String): EmbedObject {
        author = Author(name, url, icon)
        return this
    }

    fun addField(name: String, value: String, inline: Boolean): EmbedObject {
        fields.add(Field(name, value, inline))
        return this
    }
}

data class Footer(val text: String, val iconUrl: String)

data class Thumbnail(val url: String)

data class Image(val url: String)

data class Author(
        val name: String,
        val url: String,
        val iconUrl: String
)

data class Field(
        val name: String,
        val value: String,
        val isInline: Boolean
)
