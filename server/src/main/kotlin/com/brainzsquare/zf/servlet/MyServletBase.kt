package com.brainzsquare.zf.servlet

import com.brainzsquare.bcommons.log.LogUtil
import com.brainzsquare.zf.Global
import com.brainzsquare.zf.r.RHandler2
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.Logger
import java.io.IOException
import java.lang.IllegalStateException
import java.util.concurrent.Phaser
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpServletRequest


abstract class MyServletBase : HttpServlet() {
    protected abstract fun doUnexpectedError(
        hreq: HttpServletRequest, hres: HttpServletResponse,
        exception: Throwable?
    )

    companion object {
        val logger: Logger
        val rh2: RHandler2
        val devMode: Boolean
        val mapper = jacksonObjectMapper()

        const val phaserWaitTimeout = 1L //1*60*1000L

        init {
            val global = Global.global
                ?: run {
                    val msg = "should not happen. global == null."
                    LogUtil.unexpected(null, msg)
                    throw IllegalStateException(msg)
                }
            this.logger = global.logger
            this.rh2 = global.rh2
            this.devMode = global.devMode
        }
    }

    override fun init() {
        phaser.register()
    }

    override fun destroy() {
        logger.info("destroying servlet ${this.servletName}...")
        this.waitForAll()
        super.destroy()
    }

////////

    protected fun beginDo() {
        phaser.register()
    }

    protected fun endDo() {
        phaser.arriveAndDeregister()
    }

    protected fun logBegin(hreq: HttpServletRequest)
        = logger.debug("begin {} from {}",
            servletName, hreq.remoteAddr ?: "<unknown>")

    protected fun logEnd(hreq: HttpServletRequest, extra: String? = null) {
        if (extra.isNullOrEmpty()) {
            logger.debug("end {} from {}",
                servletName, hreq.remoteAddr ?: "<unknown>")
        } else {
            logger.debug("end {} from {}, {}",
                servletName, hreq.remoteAddr ?: "<unknown>", extra)
        }
    }

    protected inline fun wrapDo(
        hreq: HttpServletRequest, hres: HttpServletResponse,
        block: ()->Unit
    ) {
        return try {
            logBegin(hreq)
            beginDo()
            val result = block()
            logEnd(hreq)
            result
        } catch (ioe: IOException) {
            logEnd(hreq, "by IOException")
            throw ioe
        } catch (t: Throwable) {
            logEnd(hreq, "by unexpected exception")
            //eh.unexpected(t)
            this.doUnexpectedError(hreq, hres, t)
            throw t
        } finally {
            endDo()
        }
    }

//    fun defaultDoUnexpectedError(
//        hreq: HttpServletRequest, hres: HttpServletResponse,
//        fmt: String = "",
//        vararg args: Any?,
//        exception: Throwable? = null)
//    {
//        val sb = StringBuilder()
//        sb.append("servlet: ").append(this.servletName)
//        if (fmt.isNotEmpty()) {
//            if (sb.isNotEmpty()) sb.append(' ')
//            sb.append("msg: ")
//                .append(MessageFormatter.arrayFormat(fmt, args).message)
//        }
//        if (exception != null) {
//            if (sb.isNotEmpty()) sb.append(' ')
//            sb.append("exception: ").append(exception.toString())
//        }
//        val msg = sb.toString()
//
//        val from = hreq.remoteAddr ?: "<unknown>"
//        logger.info("from: {}, {}", from, msg)
//        if (hres.isCommitted) {
//            // Nothing we can do in this case
//            logger.info("cannot send error. response already commited.")
//            return
//        }
//        try {
//            // cannot do hres.reset(), since it will reset all CORS headers
//            // hres.doError(code, msg) does dispatch
//            hres.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
//            hres.writer.println(msg)
//        } catch(ioe: IOException) {
//            logger.info("IOException while sending unexpected error to $from")
//            throw ioe
//        } catch (e: Exception) {
//            LogUtil.unexpected(logger, e, "while sending unexpected error to $from")
//        }
//    }

////////

    private val phaser = Phaser()

    private fun waitForAll() {
        logger.info("waiting for all servlets {} to finish, for max {} msec...",
            this.servletName, phaserWaitTimeout)
        try {
            val v = phaser.arriveAndDeregister()
            phaser.awaitAdvanceInterruptibly(v, phaserWaitTimeout, TimeUnit.MILLISECONDS)
            logger.info("all servlets {} finished.", this.servletName)
        } catch (e: InterruptedException) {
            logger.error("interrupted. destroying the servlet {} anyway. may result in errors.",
                this.servletName)
        } catch (e: TimeoutException) {
            logger.error("some servlets {} have not finished. destroying the servlet anyway. may result in errors.",
                this.servletName)
        } finally {
            phaser.forceTermination()
        }
    }

}
