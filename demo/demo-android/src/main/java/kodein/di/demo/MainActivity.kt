package kodein.di.demo

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kodein.di.demo.coffee.Coffee
import kodein.di.demo.coffee.Kettle
import kodein.di.demo.coffee.thermosiphonModule
import org.kodein.di.android.closestKodein
import org.kodein.di.android.retainedKodein
import org.kodein.di.*
import org.kodein.di.generic.instance
import org.kodein.di.generic.kcontext

class MainActivity : AppCompatActivity(), KodeinAware {

    override val kodeinContext: KodeinContext<*> = kcontext(this)

//    private val _parentKodein by closestKodein()
//
//    override val kodein: Kodein by retainedKodein {
//        extend(_parentKodein, copy = Copy.All)
//        import(thermosiphonModule)
//    }

    override val kodein by closestKodein()

//    val kodein by lazy { (application as DemoApplication).kodein }

    // will be the same instance as the coffeeMaker in MainFragment
    val coffeeMaker: Kettle<Coffee> by activityCoffeeMakerInstance()
//    val log: Logger by instance()

    fun activityCoffeeMakerInstance(): KodeinProperty<Kettle<Coffee>> {
        return instance<Kettle<Coffee>>()
    }

//    inline fun <reified T: Any> instance(): KodeinProperty<T> = kodein.instance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        println("ACT: ${hashCode()} - ${kodein.hashCode()}")
        println("ACT CoffeeMaker: ${coffeeMaker.hashCode()}")

        if(savedInstanceState == null) {
//            log.log("Going to brew coffee using $coffeeMaker")

            supportFragmentManager.beginTransaction().add(R.id.fragment, MainFragment()).commit()
        }

        Log.i("Kodein", "=====================-BINDINGS-=====================")
        Log.i("Kodein", kodein.container.tree.bindings.description())
        Log.i("Kodein", "====================================================")
    }

}
