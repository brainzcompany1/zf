//package com.brainzsquare.bcommons.error
//
//import com.brainzsquare.bcommons.log.LogUtil
//import org.slf4j.Logger
//
//
//interface ErrorHandler {
//    companion object {
//        fun createLoggerIEH(logger: Logger) : ErrorHandler {
//            return LoggerEH(logger)
//        }
//    }
//
//    fun unexpected(
//        fmt: String = "", vararg args : Any?,
//        offset: Int = 2,
//        fullStacktrace: Boolean = true,
//        exception: Throwable? = null)
//        = error(
//            fmt, *args,
//            offset = offset+1, fullStacktrace = fullStacktrace,
//            msg0 = LogUtil.msgShouldNotHappen,
//            exception = exception)
//
//    fun unexpected(t: Throwable)
//        = error(
//            offset = 2+1,
//            msg0 = LogUtil.msgShouldNotHappen,
//            exception = t)
//
//    fun unexpected()
//        = error(
//            offset = 2+1,
//            msg0 = LogUtil.msgShouldNotHappen)
//
//    fun error(t: Throwable)
//        = error("", offset = 2+1, exception = t)
//
//    fun error(
//        fmt: String = "", vararg args : Any?,
//        offset: Int = 1, fullStacktrace: Boolean = false,
//        msg0: String = "",
//        exception: Throwable? = null)
//
//    fun shutDown()
//}
//
//
//class LoggerEH internal constructor(private val logger: Logger) : ErrorHandler {
//    override fun error(
//        fmt: String, vararg args : Any?,
//        offset: Int, fullStacktrace: Boolean, msg0: String, exception: Throwable?)
//        = LogUtil.error(
//            logger, fmt, *args,
//            offset = offset+1, fullStacktrace = fullStacktrace,
//            msg0 = msg0,
//            exception = exception)
//
//    override fun shutDown() {}
//}
