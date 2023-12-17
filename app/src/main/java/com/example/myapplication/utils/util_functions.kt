package com.example.myapplication.utils

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

fun assetFilePath(context: Context, asset: String): String {
    val file = File(context.filesDir, asset)

    try {
        val inpStream: InputStream = context.assets.open(asset)
        try {
            val outStream = FileOutputStream(file, false)
            val buffer = ByteArray(4 * 1024)
            var read: Int

            while (true) {
                read = inpStream.read(buffer)
                if (read == -1) {
                    break
                }
                outStream.write(buffer, 0, read)
            }
            outStream.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return ""
}