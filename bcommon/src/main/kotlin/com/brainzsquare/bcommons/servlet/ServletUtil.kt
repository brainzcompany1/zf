package com.brainzsquare.bcommons.servlet

import com.brainzsquare.bcommons.error.MyErr
import com.brainzsquare.bcommons.log.LogUtil
import javax.servlet.ServletContext
import javax.servlet.http.HttpServletRequest
import org.slf4j.Logger


object ServletUtil {
    val ecServletContextInitParam = MyErr.register("ecServletContextInitParam")

    fun getBooleanOrDefault(err: MyErr, sc: ServletContext, key: String, defaultValue: Boolean): Boolean {
        val value = sc.getInitParameter(key)
            ?: return defaultValue
        return when {
            value.equals("true", ignoreCase = true) -> true
            value.equals("false", ignoreCase = true) -> false
            else -> {
                err.newErr(ecServletContextInitParam, "invalid init param $key. not a Boolean: $value")
                defaultValue
            }
        }
    }

    fun getIntOrDefault(err: MyErr, sc: ServletContext, key: String, defaultValue: Int = Int.MIN_VALUE) : Int {
        val value = sc.getInitParameter(key)
            ?: return defaultValue
        return runCatching { value.toInt() }
            .getOrElse {
                err.newErr(ecServletContextInitParam, "invalid init param $key. not an Int: $value")
                return defaultValue
            }
    }

    fun servletParamOrDefault(sc: ServletContext, key: String, defaultValue: String = "") : String
        = sc.getInitParameter(key) ?: defaultValue

    @JvmOverloads
    fun getAttributeString(
        logger: Logger?, hreq: HttpServletRequest,
        key: String, defaultValue: String = ""
    ): String {
        val o = hreq.getAttribute(key)
        return when (o) {
            null -> defaultValue
            !is String -> {
                LogUtil.info(logger, "attribute {} is not String: {}",
                    key, o.javaClass)
                defaultValue
            }
            else -> o
        }
    }

    fun servletParamOrDefault(hreq: HttpServletRequest, key: String, def: String = ""): String
        = hreq.getParameter(key) ?: def
}
