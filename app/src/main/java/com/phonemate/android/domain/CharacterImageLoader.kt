package com.phonemate.android.domain

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import java.io.File
import pl.droidsonroids.gif.GifDrawable

fun loadCharacterDrawable(context: Context, source: CharacterImageSource): Drawable? {
    return runCatching {
        when (source) {
            is CharacterImageSource.Asset -> {
                if (source.path.endsWith(".gif", ignoreCase = true)) {
                    GifDrawable(context.assets, source.path)
                } else {
                    context.assets.open(source.path).use { stream ->
                        Drawable.createFromStream(stream, source.path)
                    }
                }
            }
            is CharacterImageSource.FilePath -> {
                if (source.path.endsWith(".gif", ignoreCase = true)) {
                    GifDrawable(File(source.path))
                } else {
                    Drawable.createFromPath(Uri.fromFile(File(source.path)).path)
                }
            }
        }
    }.getOrNull()
}

fun loadCharacterDrawable(context: Context, uri: Uri): Drawable? {
    return runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@runCatching null
        if (context.contentResolver.getType(uri) == "image/gif") {
            GifDrawable(bytes)
        } else {
            Drawable.createFromStream(bytes.inputStream(), uri.toString())
        }
    }.getOrNull()
}
