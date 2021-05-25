package com.brainzsquare.zf.r

import com.brainzsquare.bcommons.log.LogUtil
import java.lang.StringBuilder
import org.rosuda.REngine.REngineException
import org.rosuda.REngine.REXP
import org.rosuda.REngine.Rserve.RConnection
import org.rosuda.REngine.Rserve.RserveException
import org.slf4j.Logger


class RResult
    private constructor(val rexp: REXP?, val err: RError?)
{
    companion object {
        fun evalAndGet(rc: RConnection, es: String)
            = try {
                val rexp = rc.eval(es)
                if (rexp.inherits("try-error")) {
                    RResult.errExp(rexp)
                } else {
                    RResult(rexp)
                }
            } catch (rex: REngineException) {
                RResult.rException(rex)
            }

        val ok = RResult(REXP())

        fun errExp(errorExp: REXP)
            = RResult(RError.ErrExp(errorExp))
        fun rException(rex: REngineException)
            = when (rex) {
                is RserveException -> RResult(RError.RserveError(rex))
                else -> RResult(RError.RException(rex))
            }

        fun unexpected(e: Throwable?, msg: String)
            = RResult(RError.Unexpected(e, msg))
    }

    ////////

    private constructor(rexp: REXP) : this(rexp, null)
    private constructor(err: RError) : this(null, err)
}


sealed class RError {
    data class ErrExp internal constructor(val errorExp: REXP) : RError()
    data class RserveError internal constructor(val rex: RserveException) : RError()
    data class RException internal constructor(val rex: REngineException) : RError()
    data class Unexpected internal constructor(val e: Throwable?, val msg0: String) : RError()

    val msg: String
        get() {
            val sb = StringBuilder()
            when (this) {
                is ErrExp -> LogUtil.error(sb, "R error. {}", this.errorExp.asString(), offset = 2)
                is RserveError -> LogUtil.error(sb, "RserveException", exception = this.rex, offset = 2)
                is RException -> {
                    LogUtil.error(sb, "REngineException.", exception = this.rex, offset = 2)
                }
                is Unexpected -> LogUtil.error(sb, "Unexpected error. {}", msg0, exception = this.e, offset = 2)
            }
            return sb.toString()
        }
}

inline fun RResult.onFailure(action: (RError)->Unit): RResult {
    if (this.err != null) {
        action(this.err)
    }
    return this
}
