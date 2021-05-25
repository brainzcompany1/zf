package com.brainzsquare.bcommons.error

import java.lang.RuntimeException


class MyException(val err: MyErr) : RuntimeException(err.lastMsg) {
	init {
		val lastErr = err.lastErr
		if (lastErr?.exception != null) {
			this.initCause(lastErr.exception)
		}
	}
}
