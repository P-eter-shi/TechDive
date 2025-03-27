package com.example.safeguard.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.safeguard.RecordingService
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlertReceiver : BroadcastReceiver() {
    private val TAG = "AlertReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Emergency alert triggered via receiver")

        when (intent.action) {
            "com.example.safeguard.TRIGGER_EMERGENCY" -> {
                triggerEmergencyProcess(context)
            }
            "com.example.safeguard.SCHEDULED_ALERT" -> {
                // Handle scheduled check-ins or alerts
                handleScheduledAlert(context, intent)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                // Reschedule any pending alerts after device reboot
                rescheduleAlerts(context)
            }
        }
    }

    private fun triggerEmergencyProcess(context: Context) {
        val auth = FirebaseAuth.getInstance()
        val database = FirebaseDatabase.getInstance()
        val userId = auth.currentUser?.uid

        if (userId == null) {
            Log.w(TAG, "No user logged in, cannot trigger emergency")
            return
        }

        // Get the user's current location
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                // Send emergency alert with location data
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
                        "latitude" to location.latitude,
                        "longitude" to location.longitude,
                        "timestamp" to System.currentTimeMillis(),
                        "active" to true,
                        "triggeredBy" to "receiver"
                    )

                    // Save emergency to database
                    emergencyRef.setValue(emergencyData)

                    // Start foreground service to maintain recording
                    val recordingServiceIntent = Intent(context, RecordingService::class.java)
                    recordingServiceIntent.putExtra("emergencyId", emergencyRef.key)
                    ContextCompat.startForegroundService(context, recordingServiceIntent)

                    Log.d(TAG, "Emergency alert sent and recording service started")
                }
            }
        }
    }

    private fun handleScheduledAlert(context: Context, intent: Intent) {
        val auth = FirebaseAuth.getInstance()
        val database = FirebaseDatabase.getInstance()
        val userId = auth.currentUser?.uid ?: return

        // Get any additional info passed with the intent
        val alertType = intent.getStringExtra("alertType") ?: "check-in"
        val alertId = intent.getStringExtra("alertId")

        // Update the alert status in the database
        val alertRef = database.getReference("scheduledAlerts").child(userId)

        if (alertId != null) {
            alertRef.child(alertId).child("status").setValue("triggered")
            alertRef.child(alertId).child("triggerTime").setValue(System.currentTimeMillis())
        }

        // Check if user responded to the scheduled check-in
        if (alertType == "check-in") {
            // If no response received, could trigger emergency
            val checkInRef = database.getReference("checkIns").child(userId).push()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            val checkInData = hashMapOf(
                "timestamp" to System.currentTimeMillis(),
                "formatted_time" to dateFormat.format(Date()),
                "status" to "missed",
                "alert_id" to alertId
            )

            checkInRef.setValue(checkInData)

            // Could optionally trigger emergency here if check-in was missed
            // triggerEmergencyProcess(context)
        }
    }

    private fun rescheduleAlerts(context: Context) {
        val auth = FirebaseAuth.getInstance()
        val database = FirebaseDatabase.getInstance()
        val userId = auth.currentUser?.uid ?: return

        // Query for active scheduled alerts
        val alertsRef = database.getReference("scheduledAlerts").child(userId)
        alertsRef.orderByChild("active").equalTo(true).get().addOnSuccessListener { snapshot ->
            for (childSnapshot in snapshot.children) {
                val alertId = childSnapshot.key
                val triggerTimeMillis = childSnapshot.child("triggerTimeMillis").getValue(Long::class.java) ?: 0
                val repeating = childSnapshot.child("repeating").getValue(Boolean::class.java) ?: false
                val intervalMillis = childSnapshot.child("intervalMillis").getValue(Long::class.java) ?: 0

                if (triggerTimeMillis > System.currentTimeMillis()) {
                    // Schedule the alert again using AlarmManager (helper function would go here)
                    // scheduleAlert(context, alertId, triggerTimeMillis, repeating, intervalMillis)
                    Log.d(TAG, "Rescheduled alert: $alertId")
                }
            }
        }
    }
}