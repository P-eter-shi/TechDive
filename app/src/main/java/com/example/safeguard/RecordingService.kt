package com.example.safeguard


import android.app.Notification
import androidx.core.net.toUri
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
//import java.util.TimerTask

class RecordingService : Service() {

    private lateinit var mediaRecorder: MediaRecorder
    private lateinit var audioFilePath: String
    private var isRecording = false
    private lateinit var emergencyId: String
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val timer = Timer()

    companion object {
        private const val NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "RecordingChannel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        emergencyId = intent?.getStringExtra("emergencyId") ?: return START_NOT_STICKY

        // Create foreground notification
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Start recording
        startRecording()

        // Set up periodic uploads of the recording
        //setupPeriodicUpload()

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Emergency Recording"
            val descriptionText = "Recording audio for emergency situations"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Emergency Recording Active")
            .setContentText("Your audio is being recorded and shared with emergency contacts")
            .setSmallIcon(android.R.drawable.ic_dialog_alert) // A built-in alert icon
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun startRecording() {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmm ss", Locale.getDefault())
        val audioFileName = "EMERGENCY_${dateFormat.format(Date())}"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC)

        try {
            val audioFile = File.createTempFile(audioFileName, ".mp3", storageDir)
            audioFilePath = audioFile.absolutePath

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder() // Use old constructor for older versions
            }

            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFilePath)
                prepare()
                start()
            }

            isRecording = true

        } catch (e: IOException) {
            stopSelf()
        }
    }

    /*
    private fun setupPeriodicUpload() {
         Upload audio file every 30 seconds
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                if (isRecording) {
                    uploadCurrentRecording()
                }
            }
        }, 30000, 30000) // 30 seconds interval
    }  */

    private fun uploadCurrentRecording() {
        try {
            // Stop current recording
            mediaRecorder.stop()

            // Upload to Firebase Storage
            val userId = auth.currentUser?.uid ?: return
            val fileRef = storage.reference.child("emergency_recordings")
                .child(userId)
                .child("${System.currentTimeMillis()}.mp3")

            val uploadTask = fileRef.putFile(File(audioFilePath).toUri())
            uploadTask.addOnSuccessListener {
                // Get download URL and update in database
                fileRef.downloadUrl.addOnSuccessListener { uri ->
                    database.getReference("emergencies")
                        .child(emergencyId)
                        .child("recordingUrls")
                        .push()
                        .setValue(uri.toString())
                }

                // Start a new recording
                startRecording()
            }
        } catch (e: Exception) {
            // If there's an error, try to restart recording
            startRecording()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()

        if (isRecording) {
            try {
                // Stop and release the media recorder
                mediaRecorder.stop()
                mediaRecorder.release()
                isRecording = false

                // Do a final upload of the recording
                uploadCurrentRecording()

                // Cancel the timer
                timer.cancel()
            } catch (e: Exception) {
                // Ignore exceptions during cleanup
            }
        }
    }
}