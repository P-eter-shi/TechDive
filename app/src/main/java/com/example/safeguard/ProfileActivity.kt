package com.example.safeguard

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage

class ProfileActivity : AppCompatActivity() {

    private lateinit var profileImageView: ImageView
    private lateinit var nameEditText: EditText
    private lateinit var phoneEditText: EditText
    private lateinit var emergencyContactsEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var logoutButton: Button

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var storage: FirebaseStorage

    private var selectedImageUri: Uri? = null

    // Use ActivityResultLauncher instead of deprecated startActivityForResult
    private val imagePickerLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                selectedImageUri = result.data?.data
                selectedImageUri?.let {
                    Glide.with(this)
                        .load(it)
                        .circleCrop()
                        .into(profileImageView)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        storage = FirebaseStorage.getInstance()

        // Initialize UI elements
        profileImageView = findViewById(R.id.profile_image)
        nameEditText = findViewById(R.id.name_edit_text)
        phoneEditText = findViewById(R.id.phone_edit_text)
        emergencyContactsEditText = findViewById(R.id.emergency_contacts_edit_text)
        saveButton = findViewById(R.id.save_button)
        logoutButton = findViewById(R.id.logout_button)

        // Ensure user is logged in
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Load user profile
        loadUserProfile()

        // Profile image click opens gallery
        profileImageView.setOnClickListener {
            val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            imagePickerLauncher.launch(galleryIntent)
        }

        // Save profile
        saveButton.setOnClickListener {
            saveUserProfile()
        }

        // Logout
        logoutButton.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun loadUserProfile() {
        val userId = auth.currentUser?.uid ?: return
        val userRef = database.getReference("users").child(userId)

        userRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                nameEditText.setText(snapshot.child("name").getValue(String::class.java) ?: "")
                phoneEditText.setText(snapshot.child("phone").getValue(String::class.java) ?: "")

                // Load emergency contacts
                val emergencyContacts = snapshot.child("emergencyContacts").children.mapNotNull {
                    it.getValue(String::class.java)
                }.joinToString(", ")
                emergencyContactsEditText.setText(emergencyContacts)

                // Load profile image
                val profileImageUrl = snapshot.child("profileImageUrl").getValue(String::class.java)
                if (!profileImageUrl.isNullOrEmpty()) {
                    Glide.with(this)
                        .load(profileImageUrl)
                        .circleCrop()
                        .placeholder(R.drawable.default_profile) // Ensure default_profile.png exists
                        .into(profileImageView)
                }
            }
        }
    }

    private fun saveUserProfile() {
        val userId = auth.currentUser?.uid ?: return
        val userRef = database.getReference("users").child(userId)

        val name = nameEditText.text.toString().trim()
        val phone = phoneEditText.text.toString().trim()
        val emergencyContactsText = emergencyContactsEditText.text.toString().trim()

        if (name.isEmpty()) {
            nameEditText.error = "Name is required"
            return
        }

        if (phone.isEmpty()) {
            phoneEditText.error = "Phone number is required"
            return
        }

        val emergencyContacts = emergencyContactsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        val profileData = hashMapOf(
            "name" to name,
            "phone" to phone
        )

        userRef.updateChildren(profileData as Map<String, Any>)

        val contactsRef = userRef.child("emergencyContacts")
        contactsRef.removeValue().addOnCompleteListener {
            emergencyContacts.forEachIndexed { index, contact ->
                contactsRef.child(index.toString()).setValue(contact)
            }
        }

        selectedImageUri?.let { uri ->
            val imageRef = storage.reference.child("profile_images").child("$userId.jpg")

            imageRef.putFile(uri).addOnSuccessListener {
                imageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    userRef.child("profileImageUrl").setValue(downloadUri.toString())
                }
            }
        }

        Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
    }
}