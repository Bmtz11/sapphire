package com.bachir.khutwa.shared

/**
 * App language. Arabic text is the key; English comes from [EN].
 * `tr("باقي {0} خطوة", n)` → "باقي 120 خطوة" / "120 steps to go".
 */
object Tr {
    @Volatile var en: Boolean = false
}

fun tr(key: String, vararg args: Any?): String {
    var s = if (Tr.en) EN[key] ?: key else key
    args.forEachIndexed { i, a -> s = s.replace("{$i}", a.toString()) }
    return s
}
