package com.brainzsquare.zf.servlet

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


class PingServlet: MyServletBase() {
    override fun doUnexpectedError(hreq: HttpServletRequest, hres: HttpServletResponse, exception: Throwable?) {
        hres.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
    }

    override fun doGet(hreq: HttpServletRequest, hres: HttpServletResponse)
        = doStuff(hreq, hres)

    override fun doPost(hreq: HttpServletRequest, hres: HttpServletResponse)
        = doStuff(hreq, hres)

    private fun doStuff(
        @Suppress("UNUSED_PARAMETER") hreq: HttpServletRequest,
        hres: HttpServletResponse
    ) {
        hres.status = HttpServletResponse.SC_OK
    }
}
