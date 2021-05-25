package com.brainzsquare.bcommons.log

import java.io.InputStream
import java.io.ByteArrayInputStream

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import com.brainzsquare.bcommons.error.ExceptionUtil

import com.brainzsquare.bcommons.error.MyErr
import com.brainzsquare.bcommons.misc.TimeUtil
import org.slf4j.helpers.MessageFormatter
import org.slf4j.Logger


object LogUtil {
    fun setupLogger(err: MyErr, name: String, istr: InputStream) : Logger? {
        try {
            return JoranConfigurator().run {
                val ctx = LoggerContext()
                ctx.reset()
                context = ctx
                istr.use { doConfigure(it) }
                ctx.getLogger(name)
            }
        } catch (t: Throwable) {
            err.newErr(MyErr.etUnexpected, locObj = object{})
            return null
        }
    }

    fun setupLogger(err: MyErr, name: String, cfgString: String) : Logger?
        = setupLogger(err, name, ByteArrayInputStream(cfgString.toByteArray()))

    fun shutDownLogger(logger: Logger): Boolean {
        // @bluegol 20161216 slf4j doesn't have shutDownLogger.
        // this probably is the best alternative, but depends on the actual implementation.
        (logger as ch.qos.logback.classic.Logger).loggerContext?.stop()
        return true
    }

    fun getLogLevelOrDefault(s: String?, def: String) = when {
        s == null -> def
        s.equals("TRACE", false) -> s
        s.equals("DEBUG", false) -> s
        s.equals("INFO", false) -> s
        s.equals("WARN", false) -> s
        s.equals("ERROR", false) -> s
        else -> def
    }

    fun error(
        logger: Logger?,
        fmt: String = "",
        vararg args: Any?,
        offset: Int = 1,
        fullStacktrace: Boolean = false,
        msg0: String = "",
        exception: Throwable? = null) {

        val sb = StringBuilder()
        error(sb, fmt, *args,
            offset = offset+1, fullStacktrace = fullStacktrace,
            msg0 = msg0,
            exception = exception)

        doError(logger, sb.toString())
    }

    fun error(
        sb: StringBuilder,
        fmt: String = "",
        vararg args: Any?,
        offset: Int = 1,
        fullStacktrace: Boolean = false,
        msg0: String = "",
        exception: Throwable? = null) {

        if (msg0.isNotEmpty()) {
            sb.append(msg0)
        }

        if (fmt.isNotEmpty()) {
            if (sb.isNotEmpty()) {
                sb.append(" ")
            }
            sb.append(MessageFormatter.arrayFormat(fmt, args).message)
        }

        if (sb.isNotEmpty()) {
            sb.append(" ")
        }
        putLocation(Throwable().stackTrace, sb,
            offset = offset + 1, fullStacktrace = fullStacktrace)

        if (exception != null) {
            if (sb.isNotEmpty()) {
                sb.append('\n')
            }
            ExceptionUtil.unwindException(exception, sb)
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    inline fun unexpected(logger: Logger?, fmt: String = "", vararg args: Any?)
        = LogUtil.error(
        logger, fmt, *args,
        fullStacktrace = true,
        msg0 = LogUtil.msgShouldNotHappen)

    @Suppress("NOTHING_TO_INLINE")
    inline fun unexpected(logger: Logger?, t: Throwable, fmt: String = "", vararg args: Any?)
        = LogUtil.error(
        logger, fmt, *args,
        fullStacktrace = true,
        msg0 = LogUtil.msgUnexpectedException,
        exception = t)

    @Suppress("NOTHING_TO_INLINE")
    inline fun info(logger: Logger?, fmt: String = "", vararg args: Any?) {
        if (logger == null) {
            print(TimeUtil.timestamp())
            print(" ")
            println(MessageFormatter.arrayFormat(fmt, args).message)
        } else {
            logger.info(fmt, *args)
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    inline fun debug(logger: Logger?, fmt: String = "", vararg args: Any?) {
        if (logger == null) {
            print(TimeUtil.timestamp())
            print(" ")
            println(MessageFormatter.arrayFormat(fmt, args).message)
        } else {
            logger.debug(fmt, *args)
        }
    }

    const val CONTINUE_PREFIX = "+   "
    const val msgShouldNotHappen = "*** SHOULD NOT HAPPEN. ***"
    const val msgUnexpectedException = "*** UNEXPECTED EXCEPTION ***"

    @Volatile
    var maxStackDepth: Int = 500

    fun putLocation(
        st: Array<out StackTraceElement>, sb: StringBuilder,
        offset: Int = 0,
        fullStacktrace: Boolean = false,
        prefix: String = CONTINUE_PREFIX) {

        val l = st.size
        if (offset >= l) {
            sb.append("<NO CALL STACK>")
            return
        }
        if (fullStacktrace) {
            sb.append("\nCALL STACK: ").append(st[offset])
            var i = offset + 1
            while (i < st.size && i < maxStackDepth + offset) {
                sb.append('\n').append(prefix).append(st[i])
                ++i
            }
            if (i < st.size) {
                sb.append(prefix).append("*** CALL STACK TOO DEEP ***")
            }
        } else {
            sb.append("at ").append(st[offset])
        }
    }

    ////////////////////////////////////////////////////////////////////////////

    @Suppress("NOTHING_TO_INLINE")
    private inline fun doError(logger: Logger?, msg: String) {
        if (logger == null) {
            print(TimeUtil.timestamp())
            print(" ")
            println(msg)
        } else {
            logger.error(msg)
        }
    }
}
