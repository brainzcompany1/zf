package com.brainzsquare.bcommons.error

import java.lang.StringBuilder
import org.slf4j.Logger


class MyErr {
	data class ErrType(val errCode: Int, val errCodeStr: String)

	companion object {
		val str2ErrType = HashMap<String, ErrType>()
		var currentCode = -90001

		const val ecNoErr = 1
		val etGeneral = register("ecGeneral")
		val etUnexpected = register("ecUnexpected")
		val etUnspecified = register("ecUnspecified")
		val etIOException = register("ecIOException")
		val etInterrupted = register("ecInterrupted")

		fun register(ecStr: String): ErrType {
			val ec = this.str2ErrType[ecStr]
			if (ec != null) return ec
			val ec1 = ErrType(currentCode, ecStr)
			this.str2ErrType[ecStr] = ec1
			currentCode--
			return ec1
		}
	}

	class OneErr internal constructor(
		ec: ErrType,
		val exception: Throwable?,
		val causedByPrev: Boolean = false,
		var msg: String = "",
		locObj: Any?
	) {
		val errCode = ec.errCode
		val errCodeStr = ec.errCodeStr

		var location: String
			private set

		fun addMsg(msg: String) {
			this.msg += " $msg"
		}

		fun addLocation(locObj: Any) {
			val newLoc = this.locationStr(locObj)
			if (newLoc.isNotEmpty()) {
				if (this.location.isEmpty()) {
					this.location = "    $newLoc"
				} else {
					this.location += "\n+   $newLoc"
				}
			}
		}

		fun log(sb: StringBuilder) {
			if (this.causedByPrev) {
				sb.append("**** CAUSING THE NEXT ERROR ****")
			} else {
				sb.append("\n")
			}
			if (this.errCode != etGeneral.errCode) {
				sb.append("errCode: ").append(this.errCodeStr)
					.append("(").append(this.errCode).append(")")
			} else {
				sb.append("errCode: ").append(this.errCodeStr)
			}
			val exc = this.exception
			if (exc != null && exc !is MyException) {
				sb.append(" ").append("exception: ").append(exc.javaClass.name)
				val excMsg = exc.message
				if (! excMsg.isNullOrEmpty()) {
					sb.append(" exception msg: ").append(excMsg)
				}
			}
			if (this.msg.isNotEmpty())
				sb.append(" msg: ").append(this.msg)
			if (this.location.isNotEmpty()) {
				sb.append(" ").append("at:\n").append(this.location).append("\n")
			}
		}

		init {
			this.location = this.locationStr(locObj ?: this.exception)
		}

		private fun locationStr(locObj: Any?): String {
			when (locObj) {
				null -> return ""
				is Throwable -> {
					val st = locObj.stackTrace
					return if (st != null && st.isNotEmpty()) {
						val ste = st[0]
						"${ste.className}.${ste.methodName}()"
					} else {
						""
					}
				}
				else -> {
					val c = locObj.javaClass.enclosingClass
					if (c != null) {
						val cons = locObj.javaClass.enclosingConstructor
						if (cons != null) return "${c.name}.<init>()"
						val m = locObj.javaClass.enclosingMethod
						if (m != null) return "${c.name}.${m.name}()"
					}
					return ""
				}
			}
		}
	}

	fun newErrGeneral(
		msg: String? = null, exception: Throwable? = null,
		causedByPrev: Boolean = false,
		locObj: Any? = null
	) {
		this.newErr(etGeneral, msg, exception, causedByPrev, locObj)
	}

	fun newErrUnexpected(
		msg: String? = null, exception: Throwable? = null,
		causedByPrev: Boolean = false,
		locObj: Any? = null
	) {
		this.newErr(etUnexpected, msg, exception, causedByPrev, locObj)
	}

	fun newErr(
		ec: ErrType,
		msg: String? = null, exception: Throwable? = null,
		causedByPrev: Boolean = false,
		locObj: Any? = null
	) {
		val _msg = if (msg == null && exception != null) {
			exception.message ?: ""
		} else {
			msg ?: ""
		}
		val err = OneErr(ec, exception, causedByPrev, _msg, (locObj ?: exception))
		this.errs.add(err)
	}

	fun copyFrom(that: MyErr) {
		for (err in that.errs) {
			this.errs.add(err)
		}
	}

	val lastErr: OneErr?
		get() = this.errs.lastOrNull()

	val lastMsg: String
		get() {
			val lastErr = this.lastErr ?: return ""
			return lastErr.msg
		}

	val errCode: Int
		get() { return (this.lastErr ?: return ecNoErr).errCode }

	fun addMsg(msg: String): MyErr {
		(this.lastErr ?: return this).addMsg(msg)
		return this
	}

	fun addLocation(locObj: Any): MyErr {
		(this.lastErr ?: return this).addLocation(locObj)
		return this
	}

	fun log(logger: Logger?) {
		val sb = StringBuilder()
		this.log(sb)
		if (sb.isEmpty()) return
		if (logger != null) {
			logger.error(sb.toString())
		} else {
			println(sb.toString())
		}
	}

	fun log(sb: StringBuilder) {
		for (err in this.errs) {
			err.log(sb)
		}
	}

	// be careful when using this! this doesn't tell you whether new error occurred or not.
	val isError: Boolean
		get() = this.lastErr != null

	var debugString: String? = null

	////////

	private val errs = ArrayList<OneErr>()
}
