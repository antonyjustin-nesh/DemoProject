package live.nesh.music.phone

import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity


class ActivityD : AppCompatActivity() {

    lateinit var activityManager: ActivityManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_d)

        Log.d("Test","Activity - C onCreate Called")

        val btnStartA = findViewById<Button>(R.id.btnStartA)
        val btnStartB = findViewById<Button>(R.id.btnStartB)
        val btnStartC = findViewById<Button>(R.id.btnStartC)
        val btnStartD = findViewById<Button>(R.id.btnStartD)
        val btnFinish = findViewById<Button>(R.id.btnFinish)

        activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager

        // START SERVICE
        btnStartA.setOnClickListener {
            val intent = Intent(this, ActivityA::class.java)
            startActivity(intent)
        }

        btnStartB.setOnClickListener {
            val intent = Intent(this, ActivityB::class.java)
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }

        btnStartC.setOnClickListener {
            val intent = Intent(this, ActivityC::class.java)
            startActivity(intent)
        }

        btnStartD.setOnClickListener {
            val intent = Intent(this, ActivityD::class.java)
            startActivity(intent)
        }

        // STOP SERVICE
        btnFinish.setOnClickListener {
            finish()
        }

    }

    override fun onStart() {
        super.onStart()
        getAppTaskState()
        Log.d("Test","Activity - D onStart Called")
    }

    override fun onPause() {
        super.onPause()
        Log.d("Test","Activity - D onPause Called")
    }

    override fun onRestart() {
        super.onRestart()
        Log.d("Test","Activity - D onRestart Called")
    }

    override fun onResume() {
        super.onResume()
        Log.d("Test","Activity - D onResume Called")
    }

    override fun onStop() {
        super.onStop()
        Log.d("Test","Activity - D onStop Called")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("Test","Activity - D onDestroy Called")
    }


    fun getAppTaskState() {
        val tasks = activityManager.appTasks.size
        val totalNumberOfTasks = activityManager.getRunningTasks(10).size

        Log.d("Test"," tasks count - $tasks totalNumberOfTasks - $totalNumberOfTasks")


        val taskInfo:List<ActivityManager.RunningTaskInfo> = activityManager.getRunningTasks(10)
        taskInfo.forEach { info ->
            Log.d("Test","Task Id: ${info.id} Number of Activities: ${info.numActivities}")
        }
    }

}
