package com.brainzsquare.zf.forecast

import com.brainzsquare.bcommons.error.MyErr
import com.brainzsquare.zf.r.*
import com.brainzsquare.zf.servlet.ForecastServlet
import org.slf4j.Logger


class ForecastResult
	private constructor(
		val forecast: DoubleArray?,

		val pi1: DoubleArray? = null,
		val pi2: DoubleArray? = null,

		val errCode: String = "",
		val errMsg: String = ""
	)
{
	companion object {
		fun create(rr: RResult): ForecastResult {
			val rerr = rr.err
			if (rerr != null) return errResult(rerr.javaClass.simpleName, msg = rerr.msg)

			val rexp = rr.rexp ?: return ForecastResult.unexpected("rerr == null && rexp == null")
			if (! rexp.isList ) return ForecastResult.unexpected("rexp is NOT a list")
			val l = rexp.asList()
			if (l.at("model") == null) return ForecastResult.cannotComputeModel()
			val forecast = RUtil.doubleVector(l.at("forecast"))
				?: return ForecastResult.unexpected("not a double vector. name: result\$forecast")
			val siz = forecast.size
			val lower1 = RUtil.doubleVector(l.at("lower1"))
				?: return ForecastResult.unexpected("not a double vector. name: result\$lower1")
			val upper1 = RUtil.doubleVector(l.at("upper1"))
				?: return ForecastResult.unexpected("not a double vector. name: result\$upper1")
			val lower2 = RUtil.doubleVector(l.at("lower2"))
				?: return ForecastResult.unexpected("not a double vector. name: result\$lower2")
			val upper2 = RUtil.doubleVector(l.at("upper2"))
				?: return ForecastResult.unexpected("not a double vector. name: result\$upper2")
			if (lower1.size != siz || upper1.size != siz || lower2.size != siz || upper2.size != siz) {
				return ForecastResult.unexpected("length mismatch. $siz ${lower1.size} ${upper1.size} ${lower2.size} ${upper2.size}")
			}
			val pi1 = DoubleArray(siz*2)
			val pi2 = DoubleArray(siz*2)
			for (i in 0 until siz) {
				pi1[i * 2] = lower1[i]
				pi1[i * 2 + 1] = upper1[i]
				pi2[i * 2] = lower2[i]
				pi2[i * 2 + 1] = upper2[i]
			}
			return ForecastResult(forecast, pi1, pi2)
		}

		fun busy(msg: String? = null): ForecastResult
			= errResult("Busy", msg = msg)

		fun inputError(msg: String? = null, exception: Throwable? = null): ForecastResult
			= errResult("InputError", msg, exception)

		fun jobTimeout(timeout: Long): ForecastResult
			= errResult("JobTimeout", msg = "timeout: $timeout msec")

		fun cannotComputeModel(): ForecastResult
			= errResult("cannotComputeModel", msg = "cannot compute model")

		fun unexpected(msg: String? = null, exception: Throwable? = null): ForecastResult
			= errResult(RError.Unexpected::class.java.simpleName, msg, exception)

		////////

		private fun errResult(errCodeStr: String, msg: String? = null, t: Throwable? = null): ForecastResult {
			val sb = StringBuilder()
			if (t != null) {
				sb.append(t.message ?: "")
			}
			if (msg != null && msg.isNotEmpty()) {
				if (sb.isNotEmpty()) sb.append(" ")
				sb.append(msg)
			}
			return ForecastResult(null, errCode = errCodeStr, errMsg = sb.toString())
		}
	}
}
