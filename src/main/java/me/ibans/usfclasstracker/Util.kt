package me.ibans.usfclasstracker

import com.diogonunes.jcolor.Ansi
import com.diogonunes.jcolor.Attribute

infix fun String.colored(color: Attribute): String {
    return Ansi.colorize(this, color)
}