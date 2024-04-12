package com.example.firebaseauthentication;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class Register extends AppCompatActivity {

    TextInputEditText editTextEmail, editTextPwd, editTextConfirmPwd;
    RadioButton radioTeacher, radioStudent;
    RadioGroup roleRadioGroup;
    Button btnRegister;
    FirebaseAuth mAuth;
    ProgressBar progressBar;
    TextView textView;
    CheckBox checkBoxViewPwd;
    FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        editTextEmail = findViewById(R.id.email);
        editTextPwd = findViewById(R.id.pwd);
        btnRegister = findViewById(R.id.registerBtn);
        progressBar = findViewById(R.id.progress);
        textView = findViewById(R.id.loginNow);
        checkBoxViewPwd = findViewById(R.id.checkBoxViewPwd);
        editTextConfirmPwd = findViewById(R.id.confirmPwd);
        radioTeacher = findViewById(R.id.radioTeacher);
        radioStudent = findViewById(R.id.radioStudent);
        roleRadioGroup = findViewById(R.id.roleRadioGroup);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        editTextEmail.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // This method is intentionally empty
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // This method is intentionally empty
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (isValidEmail(s.toString())) {
                    editTextEmail.getBackground().setColorFilter(Color.GREEN, PorterDuff.Mode.SRC_ATOP);
                    editTextPwd.setEnabled(true);
                    // Enable other components here if necessary
                } else {
                    editTextEmail.getBackground().setColorFilter(Color.RED, PorterDuff.Mode.SRC_ATOP);
                    editTextPwd.setEnabled(false);
                    // Disable other components here if necessary
                }
            }

            private boolean isValidEmail(CharSequence target) {
                return (!TextUtils.isEmpty(target) && Patterns.EMAIL_ADDRESS.matcher(target).matches());
            }
        });

        checkBoxViewPwd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkBoxViewPwd.isChecked()) {
                    // Show Passwords
                    editTextPwd.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                    editTextConfirmPwd.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                } else {
                    // Hide Passwords
                    editTextPwd.setTransformationMethod(PasswordTransformationMethod.getInstance());
                    editTextConfirmPwd.setTransformationMethod(PasswordTransformationMethod.getInstance());
                }
                // This is to ensure the cursor stays at the end of the text
                editTextPwd.setSelection(editTextPwd.getText().length());
                editTextConfirmPwd.setSelection(editTextConfirmPwd.getText().length());
            }
        });

        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                progressBar.setVisibility(View.VISIBLE);

                final String email = editTextEmail.getText().toString();
                String password = editTextPwd.getText().toString();
                String confirmPassword = editTextConfirmPwd.getText().toString();

                // Check if passwords match
                if (!password.equals(confirmPassword)) {
                    Toast.makeText(Register.this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Check if a role is selected
                int selectedRoleId = roleRadioGroup.getCheckedRadioButtonId();
                if (selectedRoleId == -1) {
                    Toast.makeText(Register.this, "Please select a role", Toast.LENGTH_SHORT).show();
                    return;
                }

                final String role = (selectedRoleId == R.id.radioTeacher) ? "teacher" : "student";

                if (TextUtils.isEmpty(email)) {
                    Toast.makeText(Register.this, "Enter Email", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (TextUtils.isEmpty(password)) {
                    Toast.makeText(Register.this, "Enter Password", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Check if the "users" collection exists, if not create it
                DocumentReference docRef = db.collection("users").document("sample");
                docRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (task.isSuccessful()) {
                            DocumentSnapshot document = task.getResult();
                            if (!document.exists()) {
                                // Create the "users" collection
                                db.collection("users").document("sample")
                                        .set(new HashMap<>())
                                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                                            @Override
                                            public void onSuccess(Void aVoid) {
                                                // Proceed with user registration
                                                registerUser(email, password, role);
                                            }
                                        })
                                        .addOnFailureListener(new OnFailureListener() {
                                            @Override
                                            public void onFailure(@NonNull Exception e) {
                                                progressBar.setVisibility(View.GONE);
                                                Toast.makeText(Register.this, "Failed to create user collection", Toast.LENGTH_SHORT).show();
                                            }
                                        });
                            } else {
                                // "users" collection exists, proceed with user registration
                                registerUser(email, password, role);
                            }
                        } else {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(Register.this, "Failed to check user collection existence", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });

        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(), Login.class);
                startActivity(intent);
                finish();
            }
        });
    }

    private void registerUser(String email, String password, String role) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        progressBar.setVisibility(View.GONE);
                        if (task.isSuccessful()) {
                            // Account creation successful
                            // Store user's role along with other data
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
                                String userId = user.getUid();
                                Map<String, Object> userData = new HashMap<>();
                                userData.put("email", email);
                                userData.put("role", role); // Add role information
                                // Store user data in Firestore
                                FirebaseFirestore.getInstance().collection("users")
                                        .document(userId)
                                        .set(userData)
                                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                                            @Override
                                            public void onSuccess(Void aVoid) {
                                                Toast.makeText(Register.this, "Account Created", Toast.LENGTH_SHORT).show();
                                                Intent intent = new Intent(Register.this, ProfileActivity.class);
                                                intent.putExtra("email", email); // Pass email to ProfileActivity
                                                intent.putExtra("role", role); // Pass role to ProfileActivity
                                                startActivity(intent);
                                                finish();
                                            }
                                        })
                                        .addOnFailureListener(new OnFailureListener() {
                                            @Override
                                            public void onFailure(@NonNull Exception e) {
                                                Toast.makeText(Register.this, "Failed to store user data", Toast.LENGTH_SHORT).show();
                                            }
                                        });
                            }
                        } else {
                            // Account creation failed
                            Toast.makeText(Register.this, "Authentication failed.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }
}
