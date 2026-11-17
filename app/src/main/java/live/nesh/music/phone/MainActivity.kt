package live.nesh.music.phone

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d("Test","Activity - A onCreate Called")

        val btnStart = findViewById<Button>(R.id.btnStart)


        // START SERVICE
        btnStart.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

    }

    override fun onStart() {
        super.onStart()
        Log.d("Test","Activity - A onStart Called")
    }

    override fun onPause() {
        super.onPause()
        Log.d("Test","Activity - A onPause Called")
    }

    override fun onRestart() {
        super.onRestart()
        Log.d("Test","Activity - A onRestart Called")
    }

    override fun onResume() {
        super.onResume()
        Log.d("Test","Activity - A onResume Called")
    }

    override fun onStop() {
        super.onStop()
        Log.d("Test","Activity - A onStop Called")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("Test","Activity - A onDestroy Called")
    }

}
