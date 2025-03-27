package com.example.safeguard

import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import androidx.core.net.toUri

class EmergencyResponseActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var userNameTextView: TextView
    private lateinit var statusTextView: TextView
    private lateinit var callButton: Button
    private lateinit var navigateButton: Button
    private lateinit var listenButton: Button
    private lateinit var stopAlarmButton: Button

    private var emergencyId: String? = null
    private var latitude: Double? = null
    private var longitude: Double? = null
    private var userName: String? = null
    private var phoneNumber: String? = null
    private var mediaPlayer: MediaPlayer? = null
    private var _isPlaying = false
    private val isPlaying: Boolean get() = _isPlaying

    private val database = FirebaseDatabase.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency_response)

        // Get data from intent
        emergencyId = intent.getStringExtra("EMERGENCY_ID")
        latitude = intent.getDoubleExtra("LATITUDE", 0.0)
        longitude = intent.getDoubleExtra("LONGITUDE", 0.0)
        userName = intent.getStringExtra("USER_NAME")

        // Initialize UI elements
        userNameTextView = findViewById(R.id.user_name_text_view)
        statusTextView = findViewById(R.id.status_text_view)
        callButton = findViewById(R.id.call_button)
        navigateButton = findViewById(R.id.navigate_button)
        listenButton = findViewById(R.id.listen_button)
        stopAlarmButton = findViewById(R.id.stop_alarm_button)

        // Set initial data
        userNameTextView.text = getString(R.string.help_message, userName)
        statusTextView.text = getString(R.string.loading_emergency_details)

        // Set up map
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.emergency_map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Load emergency details
        loadEmergencyDetails()

        // Set up buttons
        setupButtons()
    }

    private fun loadEmergencyDetails() {
        emergencyId?.let { id ->
            val emergencyRef = database.getReference("emergencies").child(id)

            emergencyRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        // Update location data if available
                        val latitudeData = snapshot.child("latitude").getValue(Double::class.java)
                        val longitudeData = snapshot.child("longitude").getValue(Double::class.java)

                        if (latitudeData != null && longitudeData != null) {
                            latitude = latitudeData
                            longitude = longitudeData
                            updateMapLocation()
                        }

                        // Get phone number for call button
                        phoneNumber = snapshot.child("userPhone").getValue(String::class.java)

                        // Update status
                        val isActive = snapshot.child("active").getValue(Boolean::class.java) == true
                        if (isActive) {
                            statusTextView.text = getString(R.string.active_emergency)
                            statusTextView.setTextColor(resources.getColor(R.color.red, theme))
                        } else {
                            statusTextView.text = getString(R.string.emergency_resolved)
                            statusTextView.setTextColor(resources.getColor(R.color.green, theme))
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    statusTextView.text = getString(R.string.error_loading_emergency)
                }
            })
        }
    }

    private fun setupButtons() {
        // Call button - direct call to the person in danger
        callButton.setOnClickListener {
            phoneNumber?.let { phone ->
                val callIntent = Intent(Intent.ACTION_DIAL)
                callIntent.data = "tel:$phone".toUri()
                startActivity(callIntent)
            } ?: run {
                statusTextView.text = getString(R.string.no_phone_number)
            }
        }

        // Navigate button - open Google Maps with directions
        navigateButton.setOnClickListener {
            if (latitude != null && longitude != null) {
                val gmmIntentUri = "google.navigation:q=$latitude,$longitude&mode=d".toUri()
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                mapIntent.setPackage("com.google.android.apps.maps")

                if (mapIntent.resolveActivity(packageManager) != null) {
                    startActivity(mapIntent)
                } else {
                    // If Google Maps is not installed, open in browser
                    val browserUri = "https://www.google.com/maps/dir/?api=1&destination=$latitude,$longitude".toUri()
                    val browserIntent = Intent(Intent.ACTION_VIEW, browserUri)
                    startActivity(browserIntent)
                }
            }
        }

        // Listen button - stream audio recordings
        listenButton.setOnClickListener {
            if (isPlaying) {
                stopAudioPlayback()
                listenButton.text = getString(R.string.listen_to_audio)
            } else {
                playLatestAudio()
                listenButton.text = getString(R.string.stop_listening)
            }
        }

        // Stop Alarm button - handle emergency resolution
        stopAlarmButton.setOnClickListener {
            // TODO: Implement logic to stop/resolve the emergency
            // This might involve updating the emergency status in Firebase
            // or notifying relevant parties
            statusTextView.text = getString(R.string.emergency_resolved)
        }
    }

    private fun playLatestAudio() {
        emergencyId?.let { id ->
            val recordingsRef = database.getReference("emergencies").child(id).child("recordingUrls")

            recordingsRef.limitToLast(1).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists() && snapshot.childrenCount > 0) {
                        val audioUrl = snapshot.children.first().getValue(String::class.java)

                        audioUrl?.let { url ->
                            try {
                                // Create and prepare MediaPlayer
                                mediaPlayer = MediaPlayer().apply {
                                    setAudioAttributes(
                                        AudioAttributes.Builder()
                                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                            .setUsage(AudioAttributes.USAGE_MEDIA)
                                            .build()
                                    )
                                    setDataSource(url)
                                    prepareAsync()

                                    setOnPreparedListener {
                                        start()
                                        _isPlaying = true
                                    }

                                    setOnCompletionListener {
                                        // Auto-fetch the next recording when this one completes
                                        stopAudioPlayback()
                                        listenButton.text = getString(R.string.listen_to_audio)
                                    }

                                    setOnErrorListener { _, _, _ ->
                                        stopAudioPlayback()
                                        statusTextView.text = getString(R.string.error_playing_audio)
                                        false
                                    }
                                }
                            } catch (e: Exception) {
                                statusTextView.text = getString(R.string.error_loading_audio, e.message)
                            }
                        } ?: run {
                            statusTextView.text = getString(R.string.no_audio_recordings)
                        }
                    } else {
                        statusTextView.text = getString(R.string.no_audio_recordings_yet)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    statusTextView.text = getString(R.string.error_loading_audio_recordings)
                }
            })
        }
    }

    private fun stopAudioPlayback() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
        _isPlaying = false
        listenButton.text = getString(R.string.listen_to_audio)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        updateMapLocation()
    }

    private fun updateMapLocation() {
        if (latitude != null && longitude != null && ::mMap.isInitialized) {
            val location = LatLng(latitude!!, longitude!!)
            mMap.clear()
            mMap.addMarker(MarkerOptions().position(location).title("$userName is here"))
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 15f))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAudioPlayback()
    }
}