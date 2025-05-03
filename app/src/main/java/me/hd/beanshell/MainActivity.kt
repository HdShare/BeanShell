package me.hd.beanshell

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import bsh.BshMethod
import bsh.Interpreter

object Tool {
    @JvmStatic
    fun log(str: String) {
        Log.d("BeanShell", str)
    }
}

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initView()
    }

    private fun initView() {
        findViewById<Button>(R.id.btnTest).setOnClickListener {
            runCatching {
                val interpreter = Interpreter().apply {
                    set("str", "Hello World!")
                    nameSpace.setMethod(
                        "log",
                        BshMethod(Tool::class.java.getMethod("log", String::class.java), Tool)
                    )
                }
                interpreter.eval("log(str);")
            }.onFailure { e ->
                Log.e("BeanShell", "Execution Failed", e)
            }
        }
    }
}