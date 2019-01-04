package kodein.di.demo

import android.util.Log

class Logger {

    var text: String = ""
        private set

    var callback: (() -> Unit)? = null

    fun log(msg: String) {
        Log.d("Logger", msg)
        text += "$msg\n"
        callback?.invoke()
    }
}
