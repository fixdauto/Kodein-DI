package kodein.di.demo.coffee

import kodein.di.demo.Logger
import org.kodein.di.bindings.ScopeCloseable

interface Ration {
    fun name(): String
}

class Coffee(log: Logger) : Ration {
    init { log.log("<Creating CoffeeRation>") }
    override fun name(): String = "coffee"
}

class Tea(log: Logger) : Ration {
    init { log.log("<Creating TeaRation>") }
    override fun name(): String = "tea"
}

class Kettle<T : Ration>(
        private val log: Logger,
        private val heater: Heater,
        private val pump: Pump,
        private val ration: () -> T // We will need a new ration every time
) : ScopeCloseable {

    init {
        log.log("<Creating CoffeeMaker>")
    }

    fun brew() {
        heater.on()
        pump.pumpWater()
        val ration = ration()
        log.log("[_]P ${ration.name()} ${System.identityHashCode(ration)} [_]P")
        heater.off()
    }

    override fun close() {
        log.log("closing kettle")
    }
}
