package com.laputa.rtmp

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private val show = Show()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Example of a call to a native method

        tv_01.setOnClickListener {
            val url = "rtmp://sendtc3.douyu.com/live"
            //val code = "9836699roUWnoJzt?wsSecret=11c5fa1ef5ef3de70d7c8d9ca1603e97&wsTime=60af6ad6&wsSeek=off&wm=0&tw=0&roirecognition=0&record=flv&origin=tct"
            val code =
                "9836699raFt3yc1W?wsSecret=c6652eb839bf4d38133ac9ee663105dd&wsTime=60b071d2&wsSeek=off&wm=0&tw=0&roirecognition=0&record=flv&origin=tct"


            show.start(this, /*"$url/$code"*/ "rtmp://169.254.255.97/laputa/abcd")
        }

        tv_02.setOnClickListener {
            show.stopLive()
        }
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {
        // Used to load the 'native-lib' library on application startup.
        init {
            System.loadLibrary("native-lib")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        show.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        super.onDestroy()
        show.stopLive()
    }
}
