package com.brainzsquare.bcommons.error

import com.brainzsquare.bcommons.log.LogUtil


object ExceptionUtil {
    fun unwindException(t: Throwable, sb: StringBuilder) {
        var count = 0
        var t1: Throwable? = t
        while (t1 != null) {
            if (count >= 1) {
                sb.append("\n*** CAUSED BY ***\n")
            }
            doGetDetail(t1, sb)
            ++count
            if (count >= maxExceptionDepth) {
                sb.append("\n*** EXCEPTION CHAIN TOO LONG ***")
                break
            }
            t1 = t1.cause
        }
        if (count > 1) {
            sb.append("\n*** EXCEPTION CHAIN LENGTH=").append(count)
                .append(" ***")
        }
    }

    private const val maxExceptionDepth = 10

    private fun doGetDetail(t: Throwable, sb: StringBuilder) {
        sb.append("exception: ").append(t.javaClass)
        if (! t.message.isNullOrEmpty()) {
            sb.append(", msg: ").append(t.message).append(" ")
        }
        LogUtil.putLocation(t.stackTrace, sb, fullStacktrace = true)
    }
}
