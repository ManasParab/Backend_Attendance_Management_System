package com.example.firebaseauthentication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

public class Login extends AppCompatActivity {

    TextInputEditText editTextEmail, editTextPwd;
    Button btnLogin;
    FirebaseAuth mAuth;
    FirebaseFirestore db;
    ProgressBar progressBar;
    TextView textView, forgotPwd;
    CheckBox checkBoxViewPwd;
    String email, password;
    boolean canLogin;

    @Override
    public void onStart() {
        super.onStart();
        // Check if user is signed in (non-null) and update UI accordingly.
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
            startActivity(intent);
            finish();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        editTextEmail = findViewById(R.id.email);
        editTextPwd = findViewById(R.id.pwd);
        btnLogin = findViewById(R.id.loginBtn);
        progressBar = findViewById(R.id.progress);
        textView = findViewById(R.id.registerNow);
        forgotPwd = findViewById(R.id.forgotPwd);
        checkBoxViewPwd = findViewById(R.id.checkBoxViewPwd);

        mAuth = FirebaseAuth.getInstance();

        checkBoxViewPwd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkBoxViewPwd.isChecked()) {
                    // Show Password
                    editTextPwd.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                } else {
                    // Hide Password
                    editTextPwd.setTransformationMethod(PasswordTransformationMethod.getInstance());
                }
            }
        });

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                progressBar.setVisibility(View.VISIBLE);


                email = String.valueOf(editTextEmail.getText());
                password = String.valueOf(editTextPwd.getText());

                if (TextUtils.isEmpty(email)) {
                    Toast.makeText(Login.this, "Enter Email", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (TextUtils.isEmpty(password)) {
                    Toast.makeText(Login.this, "Enter Password", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Check if the email is a student and attendance for enrolled subjects is false
                checkStudentAttendanceAndLogin(email, password);
            }
        });

        forgotPwd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetPwd();
            }
        });

        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(), Register.class);
                startActivity(intent);
                finish();
            }
        });

    }

    private void checkStudentAttendanceAndLogin(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        progressBar.setVisibility(View.GONE);
                        if (task.isSuccessful()) {
                            // Check if the logged-in user is a student and attendance for enrolled subjects is false
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
                                checkStudentAttendance(user);
                            } else {
                                Toast.makeText(Login.this, "User is null", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            // If sign in fails, display a message to the user.
                            Toast.makeText(Login.this, "Authentication failed.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void checkStudentAttendance(FirebaseUser user) {
        // Fetch the user's data from Firestore
        FirebaseFirestore.getInstance().collection("users").whereEqualTo("email", user.getEmail()).get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                String uid = document.getId();
                                String role = document.getString("role");
                                if (role != null && role.equals("student")) {
                                    // User is a student, check attendance for enrolled subjects
                                    checkAttendanceForEnrolledSubjects(uid);
                                } else {
                                    // User is not a student, login as usual
                                    loginSuccess();
                                }
                            }
                        } else {
                            Toast.makeText(Login.this, "Failed to fetch user data", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void checkAttendanceForEnrolledSubjects(String uid) {
        // Fetch enrolled subjects and check their attendance
        FirebaseFirestore.getInstance().collection("users").document(uid).collection("classes_enrolled").get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            canLogin = true;
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                String className = document.getString("className");
                                if (className != null) {
                                    FirebaseFirestore.getInstance().collection("classes").whereEqualTo("className", className)
                                            .whereEqualTo("attendance", true).get()
                                            .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                                                @Override
                                                public void onComplete(@NonNull Task<QuerySnapshot> task) {
                                                    if (task.isSuccessful()) {
                                                        for (QueryDocumentSnapshot doc : task.getResult()) {
                                                            // Attendance for at least one subject is true, prevent login
                                                            canLogin = false;
                                                            break;
                                                        }
                                                        if (!canLogin) {
                                                            // Attendance marked for at least one subject, prevent login
                                                            Toast.makeText(Login.this, "Cannot login, attendance is marked for enrolled subjects", Toast.LENGTH_SHORT).show();
                                                        } else {
                                                            // No attendance marked for any subject, allow login
                                                            loginSuccess();
                                                        }
                                                    } else {
                                                        Toast.makeText(Login.this, "Failed to fetch attendance data", Toast.LENGTH_SHORT).show();
                                                    }
                                                }
                                            });
                                }
                            }
                        } else {
                            Toast.makeText(Login.this, "Failed to fetch enrolled subjects", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void loginSuccess() {
        Toast.makeText(Login.this, "Login Successful", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void resetPwd() {
        String email = editTextEmail.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            Toast.makeText(Login.this, "Enter your email", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            Toast.makeText(Login.this, "Password reset email sent", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(Login.this, "Failed to send reset email. Check your email address.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

}