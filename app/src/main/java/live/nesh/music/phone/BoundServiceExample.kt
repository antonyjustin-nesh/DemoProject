package live.nesh.music.phone

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log

class BoundServiceExample : Service() {

    var isLoopRunning = false
    var i = 0

    inner class MyServiceBinder : Binder() {
        fun getService(): BoundServiceExample {
            return this@BoundServiceExample
        }
    }

    var mBinder: IBinder? = MyServiceBinder()


    override fun onBind(intent: Intent?): IBinder? {

        Log.d("Test", "BoundServiceExample onBind")

        return mBinder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        Log.d("Test", "BoundServiceExample onStartCommand")

        isLoopRunning = true

        Thread {
            while (true) {
                Log.d("Test", "Incremented Number - $i")
                Thread.sleep(1000)
                i = ++i
                if (!isLoopRunning) {
                    break
                }
            }
        }.start()

        return START_STICKY
    }

    fun getNumber(): Int {
        return i
    }

    override fun onDestroy() {
        super.onDestroy()
        isLoopRunning = false
        Log.d("Test", "BoundServiceExample onDestroy")
    }

}