package com.avanza.astrix.intellij

import com.intellij.openapi.util.IconLoader
import org.intellij.lang.annotations.Language

object Icons {
    object Gutter {
        val asterisk = getIcon<Icons>("/icons/gutter/asterisk.png")
    }

    private inline fun <reified T> getIcon(@Language("file-reference") path: String) =
        IconLoader.getIcon(path, T::class.java)
}