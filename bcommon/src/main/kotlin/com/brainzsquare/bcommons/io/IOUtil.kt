package com.brainzsquare.bcommons.io

import com.brainzsquare.bcommons.error.MyErr
import java.nio.ByteBuffer

import com.brainzsquare.bcommons.log.LogUtil
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import org.slf4j.Logger


object IOUtil {
    fun readAllAsUTF8String(err: MyErr, istr: InputStream): String? {
        try {
            InputStreamReader(istr, StandardCharsets.UTF_8).use {
                return it.readText()
            }
        } catch (e: IOException) {
            err.newErr(MyErr.etIOException, exception = e)
            return null
        }
    }

    inline fun withLinedStream(logger: Logger?, istr: InputStream, block: (String) -> Unit) {
        runCatching {
            BufferedReader(InputStreamReader(istr)).use {
                while (true) {
                    val line = it.readLine() ?: break
                    block(line)
                }
            }
        }.onFailure {
            when (it) {
                is IOException -> LogUtil.error(logger, "IOException while reading the stream r{}", istr)
                else -> throw it
            }
        }
    }

    fun close(logger: Logger?, a: AutoCloseable?, ignoreError: Boolean = false) {
        if (a == null) {
            return
        }
        try {
            a.close()
        } catch (e: Exception) {
            if (!ignoreError) {
                LogUtil.error(logger,
                    "exception while closing {}", a.javaClass,
                    exception = e)
            }
        }
    }
}
