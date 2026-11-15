package live.nesh.music.phone

import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity


class PersonDetails : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main1)

        Log.d("Test","onCreate() Called")

        val btnFinish = findViewById<Button>(R.id.btnFinish)

        val personDetailsSerializable = intent.getSerializableExtra("person_detail")
        val personDetails = personDetailsSerializable as Person
        Log.d("Test","personDetails - $personDetails")

        // STOP SERVICE
        btnFinish.setOnClickListener {
            finish()
        }

    }

    override fun onStart() {
        super.onStart()
        Log.d("Test","onStart() Called")
    }

    override fun onPause() {
        super.onPause()
        Log.d("Test","onPause() Called")
    }

    override fun onRestart() {
        super.onRestart()
        Log.d("Test","onRestart() Called")
    }

    override fun onResume() {
        super.onResume()
        Log.d("Test","onResume() Called")
    }

    override fun onStop() {
        super.onStop()
        Log.d("Test","onStop() Called")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("Test", "onDestroy() Called")
    }

}
