package com.mohammad.tvdraw

import android.content.Context
import java.util.Locale

/** A translated text from the strings.xml resource files. Numbers always use Western digits so scores and clocks look the same in every language. */
fun Context.str(id: Int, vararg args: Any): String =
    if (args.isEmpty()) getString(id) else String.format(Locale.US, getString(id), *args)
