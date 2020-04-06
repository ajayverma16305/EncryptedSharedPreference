package com.meekoo.obscuredsharedpreference

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.google.gson.GsonBuilder
import com.meekoo.obscuredsharedpreference.global.storage
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONObject
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    companion object {
        const val STRING = "String"
        const val INT = "Int"
        const val LONG = "Long"
        const val DOUBLE = "Double"
        const val BOOLEAN = "Boolean"
        const val OBJECT = "Object"
    }

    private data class Apple(val size: Float, val weight: Float)
    private data class Bag(var apple: List<Apple>)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ws?.setOnClickListener {
            this.writeData()
        }

        rs?.setOnClickListener {
            this.showData()
        }
    }

    private fun writeData() {
        tv3?.text = ""

        with(storage) {
            this ?: return@with

            thread {
                putString(STRING, "Ajay Verma")
                putBoolean(BOOLEAN, true)
                putDouble(DOUBLE, 10.45)
                putLong(LONG, 34)
                putInt(INT, 28)

                val apples = listOf(
                    Apple(5f, 50f),
                    Apple(6f, 55f),
                    Apple(7f, 60f),
                    Apple(8f, 65f)
                )

                val bag = Bag(apples)
                putObject(OBJECT, bag)
            }
        }
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
    }

    private fun showData() {
        with(storage) {
            this ?: return@with

            thread {
                val stringValue = getString(STRING, null)
                val booleanValue = getBoolean(BOOLEAN, false)
                val longValue = getLong(LONG, -1)
                val intValue = getInt(INT, -1)
                val doubleValue = getDouble(DOUBLE, 0.0)
                val bags = getObject<Bag>(OBJECT)

                val formattedString = GsonBuilder().create().toJson(bags)
                val jsonString = JSONObject(formattedString).toString(2)

                val finalString = """
                           $STRING : $stringValue
                           $LONG : $longValue
                           $DOUBLE : $doubleValue
                           $INT : $intValue
                           $BOOLEAN : $booleanValue
                           $OBJECT : $jsonString
                            """.trimIndent()

                tv3?.post {
                    tv3?.text = finalString
                }
            }
        }
    }
}
