package com.brainzsquare.bcommons.misc

import com.brainzsquare.bcommons.error.MyErr
import java.text.SimpleDateFormat
import java.util.Date


object TimeUtil {
    fun sleep(err: MyErr, l: Long): Int
        = try {
            Thread.sleep(l)
            1
        } catch (e: InterruptedException) {
            err.newErr(MyErr.etInterrupted, locObj = object{})
            err.errCode
        }

    fun timestamp(sdf: SimpleDateFormat?, l: Long): String {
        val f: SimpleDateFormat = sdf ?: SimpleDateFormat(timestampFormat)
        return f.format(Date(l))
    }

    fun timestamp() = timestamp(SimpleDateFormat(timestampFormat), System.currentTimeMillis())

    fun timestamp(l: Long) = timestamp(SimpleDateFormat(timestampFormat), l)

    private const val timestampFormat = "yyyyMMdd'T'HHmmss.SSSZ"
}
