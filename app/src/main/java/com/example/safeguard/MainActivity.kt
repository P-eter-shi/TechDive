package com.example.safeguard

import android.Manifest
import android.util.Log
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var mediaRecorder: MediaRecorder
    private lateinit var audioFilePath: String
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private var isRecording = false

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        // Check if user is logged in
        if (auth.currentUser == null) {
            // Redirect to login activity
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Set up Google Maps
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Subscribe to emergency notifications
        FirebaseMessaging.getInstance().subscribeToTopic("emergencies")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("Notifications", "Subscribed to emergencies topic")
                }
            }

        // Set up emergency button
        val emergencyButton = findViewById<Button>(R.id.emergency_button)
        emergencyButton.setOnClickListener {
            triggerEmergency()
        }

        // Request necessary permissions
        requestPermissions()

        // Initialize profile button
        val profileButton = findViewById<Button>(R.id.profile_button)
        profileButton.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    private fun requestPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        // Check for LOCATION permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // Check for AUDIO permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO)
        }

        // Check for BACKGROUND LOCATION (Only add if API 29+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }

        // Check for POST NOTIFICATIONS (Only add if API 33+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Request permissions if needed
        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), LOCATION_PERMISSION_REQUEST_CODE)
        }
    }


    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true

            // Get current location and update map
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val currentPos = LatLng(it.latitude, it.longitude)
                    mMap.addMarker(MarkerOptions().position(currentPos).title("Your location"))
                    updateLocationInDatabase(it.latitude, it.longitude)
                }
            }
        }
    }

    private fun updateLocationInDatabase(latitude: Double, longitude: Double) {
        val userId = auth.currentUser?.uid ?: return
        val locationRef = database.getReference("users").child(userId).child("location")

        val locationData = hashMapOf(
            "latitude" to latitude,
            "longitude" to longitude,
            "timestamp" to System.currentTimeMillis()
        )

        locationRef.setValue(locationData)
    }

    private fun triggerEmergency() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Missing permissions required for emergency", Toast.LENGTH_LONG).show()
            requestPermissions()
            return
        }

        // Get current location
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                // Send emergency alert with location data
                sendEmergencyAlert(it.latitude, it.longitude)

                // Start recording audio
                startBackgroundRecording()

                // Show confirmation to user
                Toast.makeText(this, "Emergency alert sent! Audio recording started.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun sendEmergencyAlert(latitude: Double, longitude: Double) {
        val userId = auth.currentUser?.uid ?: return
        val userRef = database.getReference("users").child(userId)

        // Get user info for alert
        userRef.get().addOnSuccessListener { snapshot ->
            val userName = snapshot.child("name").getValue(String::class.java) ?: "Unknown user"
            val userPhone = snapshot.child("phone").getValue(String::class.java) ?: "No phone"

            // Create emergency data
            val emergencyRef = database.getReference("emergencies").push()
            val emergencyData = hashMapOf(
                "userId" to userId,
                "userName" to userName,
                "userPhone" to userPhone,
                "latitude" to latitude,
                "longitude" to longitude,
                "timestamp" to System.currentTimeMillis(),
                "active" to true
            )

            // Save emergency to database
            emergencyRef.setValue(emergencyData)

            // Start foreground service to maintain recording
            val recordingServiceIntent = Intent(this, RecordingService::class.java)
            recordingServiceIntent.putExtra("emergencyId", emergencyRef.key)
            ContextCompat.startForegroundService(this, recordingServiceIntent)
        }
    }

    private fun startBackgroundRecording() {
        if (isRecording) {
            return
        }

        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmm ss", Locale.getDefault())
        val audioFileName = "AUDIO_${dateFormat.format(Date())}"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC)

        try {
            val audioFile = File.createTempFile(audioFileName, ".mp3", storageDir)
            audioFilePath = audioFile.absolutePath

            mediaRecorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
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

            // Upload reference to the recording
            val userId = auth.currentUser?.uid ?: return
            val recordingRef = database.getReference("emergencies").child(userId).child("audioRecording")
            recordingRef.setValue(audioFilePath)

        } catch (e: IOException) {
            Toast.makeText(this, "Could not start recording: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) {
            try {
                mediaRecorder.stop()
                mediaRecorder.release()
                isRecording = false
            } catch (e: Exception) {
                // Ignore exceptions during cleanup
            }
        }
    }
}
