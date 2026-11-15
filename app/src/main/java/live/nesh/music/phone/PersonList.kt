package live.nesh.music.phone

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity


class PersonList : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d("Test","onCreate() Called")

        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnUpdate = findViewById<Button>(R.id.btnUpdate)
        val btnFinish = findViewById<Button>(R.id.btnFinish)

        val person = Person(name = "TestName", gmail = "TestGmail", age = 21)

        //etSample = findViewById<EditText>(R.id.etSample)

        // START SERVICE
        btnStart.setOnClickListener {

            val intent = Intent(this, PersonDetails::class.java)
            intent.putExtra("person_detail",person)
            startActivity(intent)
        }

        // UPDATE NOTIFICATION
        btnUpdate.setOnClickListener {

        }

        // STOP SERVICE
        btnFinish.setOnClickListener {

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
        Log.d("Test","onDestroy() Called")
    }

}
