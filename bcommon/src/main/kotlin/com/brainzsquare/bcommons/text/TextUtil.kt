package com.brainzsquare.bcommons.text

import com.brainzsquare.bcommons.error.MyErr
import com.brainzsquare.bcommons.log.LogUtil
import org.slf4j.Logger


fun String.toLong(err: MyErr, errRv: Long = Long.MIN_VALUE): Long {
	return try {
		this.toLong()
	} catch (t: Throwable) {
		err.newErrGeneral("toLong failed. str: $this", exception = t)
		errRv
	}
}

object TextUtil {

	// RFC 4180 minus header plus unicode
	// plus allowing LF to kill line
	// plus allowing quote in non-Escaped
	fun splitCsvFirstLine(logger: Logger?, s: String, separator: Char = ','): ArrayList<String>? {
		val result = ArrayList<String>()
		val l = s.length
		val sb = StringBuilder()
		var state = SplitState.Initial
		var lastC = '\u0000'
		var i = 0
		while (i < l) {
			val c = s[i]
			when (state) {
				SplitState.Initial -> when (c) {
					'"' -> {
						state = SplitState.Escaped
						lastC = '\u0000'
						++i
					}
					separator -> {
						result.add("")
						state = SplitState.Initial
						lastC = '\u0000'
						++i
					}
					else -> {
						state = SplitState.NonEscaped
						sb.append(c)
						lastC = c
						++i
					}
				}

				SplitState.NonEscaped -> when (c) {
					separator -> {
						state = SplitState.Initial
						lastC = '\u0000'
						++i
						result.add(sb.toString())
						sb.clear()
					}
					'\u000d' -> {
						lastC = c
						++i
					}
					'\u000a' -> {
						// kill of line
						@Suppress("UNUSED_VALUE")
						lastC = c
						++i
						result.add(sb.toString())
						return result
					}
					else -> {
						if (lastC == '\u000d') {
							LogUtil.error(logger, "invalid CR not followed by LF, treated as kill of line")
							return null
						} else if (lastC == '"') {
							LogUtil.error(logger, "invalid quotation mark in the middle, treated as is")
						}
						sb.append(c)
						lastC = c
						++i
					}
				}

				SplitState.Escaped -> {
					if (c == '"' && lastC != '"') {
						state = SplitState.EndEscaped
						lastC = '\u0000'
						++i
					} else {
						sb.append(c)
						lastC = c
						++i
					}
				}

				SplitState.EndEscaped -> when (c) {
					separator -> {
						if (lastC != '\u0000') {
							LogUtil.error(logger, "invalid character following a quotation mark in Escaped")
							return null
						}
						state = SplitState.Initial
						lastC = '\u0000'
						++i
						result.add(sb.toString())
						sb.clear()
					}
					'\u000d' -> {
						if (lastC != '\u0000') {
							LogUtil.error(logger, "invalid CR, not following a quotation mark, in Escaped")
							return null
						}
						lastC = c
						++i
					}
					'\u000a' -> {
						return if (lastC != '\u0000' && lastC != '\u000d') {
							LogUtil.error(logger, "invalid LF, not following a quotation mark nor CR, in Escaped")
							null
						} else {
							@Suppress("UNUSED_VALUE")
							state = SplitState.Initial
							@Suppress("UNUSED_VALUE")
							lastC = '\u0000'
							++i
							result.add(sb.toString())
							sb.clear()
							result
						}
					}
					else -> {
						LogUtil.error(logger, "invalid character following the quotation mark, in Escaped")
						return null
					}
				}
			}
		}
		return when (state) {
			SplitState.Escaped -> {
				LogUtil.error(logger, "invalid end of string. state: {} s: {} i: {}",
					state, s, i)
				null
			}

			else -> {
				if (sb.isNotEmpty() || result.size > 0) {
					result.add(sb.toString())
				}
				result
			}
		}
	}

	private enum class SplitState {
		Initial,
		NonEscaped,
		Escaped,
		EndEscaped,
	}
}
