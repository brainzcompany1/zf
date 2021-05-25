package com.brainzsquare.zf.servlet

import com.brainzsquare.bcommons.error.MyErr
import com.brainzsquare.bcommons.servlet.ServletUtil
import com.brainzsquare.zf.r.RHandler2
import com.brainzsquare.zf.r.RJobBuilder
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


class MyTestServlet : MyServletBase() {
	override fun doUnexpectedError(hreq: HttpServletRequest, hres: HttpServletResponse, exception: Throwable?) {
		hres.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
	}

	override fun init() {
        super.init()
        logger.info("servlet initialized. servletName: {}", this.servletName)
    }

	override fun destroy() {
		super.destroy()
		logger.info("servlet: ${this.servletName} destroyed.")
	}

	override fun doGet(hreq: HttpServletRequest, hres: HttpServletResponse) {
        servletContext.getRequestDispatcher("/testForm.jsp").forward(hreq, hres)
    }

    override fun doPost(hreq: HttpServletRequest, hres: HttpServletResponse) {
		wrapDo(hreq, hres) {
			val doForecast = ServletUtil.servletParamOrDefault(hreq, spForecast).trim().isNotEmpty()
			val doEval = ServletUtil.servletParamOrDefault(hreq, spEval).trim().isNotEmpty()
			if (! doForecast && ! doEval) {
				val errMsg = "No input"
				hreq.setAttribute(spForecastResult, errMsg)
				hreq.setAttribute(spEvalResult, errMsg)
				servletContext.getRequestDispatcher(form).forward(hreq, hres)
				return
			}
			val attrResultKey = if (doForecast) spForecastResult else spEvalResult
			val attrInputKey = if (doForecast) spForecastInput else spEvalInput

			val inputStr = ServletUtil.servletParamOrDefault(hreq, attrInputKey).trim()
			val outputStr = if (doForecast) {
				val err = MyErr()
				if (inputStr.isEmpty()) {
					"No Input"
				} else {
					mapper.writeValueAsString(ForecastServlet.doForecast(err, inputStr))
				}
			} else {
				if (inputStr.isEmpty()) {
					"No Input"
				} else {
					val err = MyErr()
					val myR = rh2.getR(err)
					if (myR == null) {
						if (err.errCode == RHandler2.ecAllMyRBusy.errCode) {
							"Busy. rConnCount: ${rh2.rConnCount}"
						} else {
							val sb = StringBuilder()
							sb.append("cannot get myR. ")
							err.log(sb)
							sb.toString()
						}
					} else {
						val rb = RJobBuilder(true)
						for (line in inputStr.lines()) {
							val w = line.split(Regex("#->"), 2)
							if (w.size == 2) {
								rb.add(w[0], w[1], retrieve = true)
							} else {
								rb.add(w[0], retrieve = true)
							}
						}
						val rJob = rb.build()
						rJob.execute(logger, myR.rconn)
						val sb = StringBuilder()
						for (i in rJob.result.withIndex()) {
							sb.append("<${i.index}>: ")
							val rresult = i.value
							if (rresult.err != null) {
								sb.append(rresult.err.msg).append("\n")
							} else {
								sb.append(rresult.rexp!!.toDebugString()).append("\n")
							}
						}
						sb.toString()
					}
				}
			}
			hreq.setAttribute(attrInputKey, inputStr)
			hreq.setAttribute(attrResultKey, outputStr)

            servletContext.getRequestDispatcher(form).forward(hreq, hres)
        }
    }

	companion object {
		// sp = servlet param
		private val form = "/testForm.jsp"

		private val prefix = "${this::class.java.enclosingClass.name}."
		val spForecast = "${prefix}forecast"
		val spForecastInput = "${prefix}forecastInput"
		val spForecastResult = "${prefix}forecastResult"
		val spEval = "${prefix}eval"
		val spEvalInput = "${prefix}evalStr"
		val spEvalResult = "${prefix}evalResult"
	}
}
