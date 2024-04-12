package com.example.firebaseauthentication;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {

    TextView textView, facultyName, classesIndicator;
    Button logoutButton, createClassButton, joinClassButton, profileBtn, allClasses;
    TextInputEditText classNameEditText;
    FirebaseAuth auth;
    FirebaseUser user;
    FirebaseFirestore db;
    private RecyclerView subjectsRecyclerView;
    private String userRole, deptName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (isDeveloperModeEnabled()) {
            // USB debugging is enabled, take appropriate action (e.g., show a message and exit the app)
            Toast.makeText(this, "Disable Developer Mode to Use BAMS.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
            return;
        }

        textView = findViewById(R.id.user_details);
        classesIndicator = findViewById(R.id.classesIndicator);
        facultyName = findViewById(R.id.facultyName);
        logoutButton = findViewById(R.id.logout);
        profileBtn = findViewById(R.id.profileBtn);
        createClassButton = findViewById(R.id.createClassButton);
        joinClassButton = findViewById(R.id.joinClassButton);
        allClasses = findViewById(R.id.allClasses);
        classNameEditText = findViewById(R.id.classNameEditText);
        subjectsRecyclerView = findViewById(R.id.subjectsRecyclerView);
        SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            getSubjectsList();
            swipeRefreshLayout.setRefreshing(false);
        });
        auth = FirebaseAuth.getInstance();
        user = auth.getCurrentUser();
        db = FirebaseFirestore.getInstance();
        setupButtonListeners();
        if (user == null) {
            navigateToLogin();
        } else {
            textView.setText(user.getEmail());
            fetchUserName();
            checkUserRole();
            setupRecyclerView();
            fetchEnrolledSubjects();
        }

        allClasses.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (allClasses.getText().equals("All Classes")) {
                    fetchClassesAccessibleByTeacher();
                    allClasses.setText("My Classes");
                    classesIndicator.setText("All Classes");
                } else if (allClasses.getText().equals("My Classes")){
                    fetchClassesCreatedByTeacher();
                    allClasses.setText("All Classes");
                    classesIndicator.setText("My Classes");
                }
            }
        });

        checkAccessibleClasses();

    }


    private void setupRecyclerView() {
        subjectsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        SubjectAdapter adapter = new SubjectAdapter(new ArrayList<>(), this::onDeleteSubject);
        subjectsRecyclerView.setAdapter(adapter);
        getSubjectsList();
    }

    private void onDeleteSubject(String className) {
        showDeleteConfirmationDialog(className, () -> deleteSubject(className));
    }

    private void showDeleteConfirmationDialog(String className, Runnable deleteCallback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Confirm Deletion");
        builder.setMessage("Are you sure you want to delete " + className + "?");
        builder.setPositiveButton("Delete", (dialog, which) -> deleteCallback.run());
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void deleteSubject(String className) {
        db.collection("classes").whereEqualTo("className", className).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                for (DocumentSnapshot document : task.getResult()) {
                    String classId = document.getId();
                    if ("teacher".equals(userRole)) {
                        db.collection("classes").document(classId).delete().addOnSuccessListener(aVoid -> {
                            db.collection("users").document(user.getUid()).collection("classes_created").document(className).delete().addOnSuccessListener(aVoid1 -> {
                                db.collection("classes").document(classId).collection("students").get().addOnCompleteListener(task1 -> {
                                    if (task1.isSuccessful()) {
                                        for (DocumentSnapshot studentDocument : task1.getResult()) {
                                            studentDocument.getReference().delete();
                                        }
                                    } else {
                                        Log.d("DeleteSubject", "Error deleting students collection", task1.getException());
                                    }
                                });
                                getSubjectsList();
                                Toast.makeText(MainActivity.this, "Class deleted", Toast.LENGTH_SHORT).show();
                            }).addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Error deleting class from 'classes_created'", Toast.LENGTH_SHORT).show());
                        }).addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Error deleting class", Toast.LENGTH_SHORT).show());
                    }
                }
                if ("student".equals(userRole)) {
                    db.collection("users").document(user.getUid()).collection("classes_enrolled").document(className).delete().addOnSuccessListener(aVoid -> {
                        getSubjectsList();
                        Toast.makeText(MainActivity.this, "Class deleted from your enrolled classes", Toast.LENGTH_SHORT).show();
                    }).addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Error deleting class from your enrolled classes", Toast.LENGTH_SHORT).show());
                }
            } else {
                Toast.makeText(MainActivity.this, "Error finding class", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void getSubjectsList() {
        if (userRole != null) {
            if ("teacher".equals(userRole)) {
                fetchClassesCreatedByTeacher();
            } else {
                fetchClassesEnrolledByStudent();
            }
        }
    }


    private void fetchClassesEnrolledByStudent() {
        if (user != null) {
            CollectionReference classesEnrolledRef = db.collection("users").document(user.getUid()).collection("classes_enrolled");
            classesEnrolledRef.get().addOnSuccessListener(queryDocumentSnapshots -> {
                List<Map<String, String>> classes = new ArrayList<>();
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    Map<String, String> classInfo = new HashMap<>();
                    String className = document.getString("className");
                    String joinCode = document.getString("joinCode");
                    if (className != null && joinCode != null) {
                        classInfo.put("className", className);
                        classInfo.put("joinCode", joinCode);
                        classes.add(classInfo);
                    }
                }
                SubjectAdapter adapter = new SubjectAdapter(classes, this::onDeleteSubject);
                subjectsRecyclerView.setAdapter(adapter);
            }).addOnFailureListener(e -> {
                Toast.makeText(MainActivity.this, "Error loading classes", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void fetchClassesCreatedByTeacher() {
        if (user != null) {
            CollectionReference classesCreatedRef = db.collection("users").document(user.getUid()).collection("classes_created");
            classesCreatedRef.get().addOnSuccessListener(queryDocumentSnapshots -> {
                List<Map<String, String>> classes = new ArrayList<>();
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    Map<String, String> classInfo = new HashMap<>();
                    String className = document.getString("className");
                    String joinCode = document.getString("joinCode");
                    if (className != null && joinCode != null) {
                        classInfo.put("className", className);
                        classInfo.put("joinCode", joinCode);
                        classes.add(classInfo);
                        allClasses.setText("All Classes");
                        classesIndicator.setText("My Classes");
                    }
                }
                SubjectAdapter adapter = new SubjectAdapter(classes, this::onDeleteSubject);
                subjectsRecyclerView.setAdapter(adapter);
            }).addOnFailureListener(e -> {
                Toast.makeText(MainActivity.this, "Error loading classes", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void checkAccessibleClasses() {
        FirebaseFirestore.getInstance().collection("users")
                .document(user.getUid()) // Use the UID of the current user
                .collection("classes_accessible")
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        // Error occurred while fetching the sub-collection
                        // Handle the error
                        Log.e("TAG", "Error getting documents: ", error);
                        return;
                    }

                    if (value != null) {
                        if (!value.isEmpty()) {
                            // Sub-collection "classes_accessible" exists
                            // Handle the case where the sub-collection exists
                            // For example, display a message or perform further actions
                            allClasses.setVisibility(View.VISIBLE);
                        } else {
                            // Sub-collection "classes_accessible" does not exist
                            // Handle the case where the sub-collection does not exist
                            // For example, display a message or perform further actions
                            allClasses.setVisibility(View.GONE);
                        }
                    }
                });
    }


    private void fetchClassesAccessibleByTeacher() {
        if (user != null) {
            CollectionReference classesAccessibleRef = db.collection("users")
                    .document(user.getUid())
                    .collection("classes_accessible");

            classesAccessibleRef.addSnapshotListener((queryDocumentSnapshots, e) -> {
                if (e != null) {
                    // Handle errors
                    Toast.makeText(MainActivity.this, "Error fetching accessible classes: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    return;
                }

                if (queryDocumentSnapshots != null) {
                    List<Map<String, String>> classes = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Map<String, String> classInfo = new HashMap<>();
                        String className = document.getString("className");
                        String joinCode = document.getString("joinCode");
                        if (className != null && joinCode != null) {
                            classInfo.put("className", className);
                            classInfo.put("joinCode", joinCode);
                            classes.add(classInfo);
                        }
                    }
                    SubjectAdapter adapter = new SubjectAdapter(classes, this::onDeleteSubject);
                    subjectsRecyclerView.setAdapter(adapter);
                }
            });
        }
    }



    private void setupButtonListeners() {
        logoutButton.setOnClickListener(v -> logoutUser());
        createClassButton.setOnClickListener(v -> createClass());
        joinClassButton.setOnClickListener(v -> showJoinClassDialog());
        profileBtn.setOnClickListener(v -> navigateToProfile());
    }


    private void logoutUser() {
        new AlertDialog.Builder(this).setTitle("Logout").setMessage("Are you sure you want to logout?").setPositiveButton("Logout", (dialog, which) -> {
            auth.signOut();
            navigateToLogin();
        }).setNegativeButton("Cancel", null).show();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(getApplicationContext(), Login.class);
        startActivity(intent);
        finish();
    }

    private void checkUserRole() {
        if (user != null) {
            db.collection("users").document(user.getUid()).get().addOnSuccessListener(this::onUserRoleSuccess).addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Error fetching user data", Toast.LENGTH_SHORT).show());
        }
    }

    private void onUserRoleSuccess(DocumentSnapshot documentSnapshot) {
        if (documentSnapshot.exists()) {
            userRole = documentSnapshot.getString("role");
            updateUIForRole(userRole);
            getSubjectsList();
        }
    }

    private void updateUIForRole(String role) {
        if ("teacher".equals(role)) {
            createClassButton.setVisibility(View.VISIBLE);
            joinClassButton.setVisibility(View.GONE);
        } else {
            // Check if attendance is true for any enrolled subject
            joinClassButton.setVisibility(View.VISIBLE);
            createClassButton.setVisibility(View.GONE);
            disableButtonWhenAttendanceTrue();
            enableButtonWhenAttendanceFalse();
        }
    }

    private void disableButtonWhenAttendanceTrue() {
        if (user != null) {
            CollectionReference enrolledClassesRef = db.collection("users").document(user.getUid()).collection("classes_enrolled");
            enrolledClassesRef.addSnapshotListener((queryDocumentSnapshots, e) -> {
                if (e != null) {
                    Log.e("FetchAttendance", "Error fetching enrolled classes", e);
                    return;
                }

                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    String className = document.getString("className");
                    if (className != null) {
                        db.collection("classes").whereEqualTo("className", className).addSnapshotListener((queryDocumentSnapshots1, e1) -> {
                            if (e1 != null) {
                                Log.e("FetchAttendance", "Error fetching attendance status", e1);
                                return;
                            }

                            for (DocumentSnapshot doc : queryDocumentSnapshots1) {
                                Boolean attendance = doc.getBoolean("attendance");
                                if (attendance != null && attendance) {
                                    runOnUiThread(() -> logoutButton.setEnabled(false));
                                    return; // No need to continue checking other documents
                                }
                            }
                        });
                    }
                }
            });
        }
    }

    private void enableButtonWhenAttendanceFalse() {
        if (user != null) {
            CollectionReference enrolledClassesRef = db.collection("users").document(user.getUid()).collection("classes_enrolled");
            enrolledClassesRef.addSnapshotListener((queryDocumentSnapshots, e) -> {
                if (e != null) {
                    Log.e("FetchAttendance", "Error fetching enrolled classes", e);
                    return;
                }

                AtomicInteger queryCount = new AtomicInteger(queryDocumentSnapshots.size());

                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    String className = document.getString("className");
                    if (className != null) {
                        db.collection("classes").whereEqualTo("className", className).addSnapshotListener((queryDocumentSnapshots1, e1) -> {
                            if (e1 != null) {
                                Log.e("FetchAttendance", "Error fetching attendance status", e1);
                                return;
                            }

                            boolean anyAttendance = false;

                            for (DocumentSnapshot doc : queryDocumentSnapshots1) {
                                Boolean attendance = doc.getBoolean("attendance");
                                if (attendance != null && attendance) {

                                    anyAttendance = true;
                                    break;
                                }
                            }

                            if (!anyAttendance) {
                                runOnUiThread(() -> logoutButton.setEnabled(true));
                            }
                        });
                    }
                }
            });
        }
    }


    private void joinClass(final String joinCode) {
        if (joinCode.isEmpty()) {
            Toast.makeText(MainActivity.this, "Join code is required", Toast.LENGTH_SHORT).show();
            return;
        }
        db.collection("classes").document(joinCode).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document.exists()) {
                    getSubjectsList();
                    updateStudentClassesEnrolled(joinCode, document.getString("className"));
                    String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    String className = document.getString("className");
                    DocumentReference classEnrolledRef = db.collection("classes").document(joinCode).collection("students").document(userId);
                    enrollStudentInClass(userId, joinCode, className, classEnrolledRef);
                } else {
                    Toast.makeText(MainActivity.this, "Invalid join code", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(MainActivity.this, "Error checking join code", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addUserToClass(final String joinCode) {
        final String userId = user.getUid();
        db.collection("classes").document(joinCode).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot classDoc = task.getResult();
                if (classDoc.exists()) {
                    final String className = classDoc.getString("className");
                    final DocumentReference classEnrolledRef = db.collection("users").document(userId).collection("classes_enrolled").document(className);
                    classEnrolledRef.get().addOnCompleteListener(task1 -> {
                        if (task1.isSuccessful()) {
                            DocumentSnapshot document = task1.getResult();
                            if (document.exists()) {
                                Toast.makeText(MainActivity.this, "Already enrolled in class", Toast.LENGTH_SHORT).show();
                            } else {
                                enrollStudentInClass(userId, joinCode, className, classEnrolledRef);
                            }
                        } else {
                            Toast.makeText(MainActivity.this, "Failed to check class enrollment", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    Toast.makeText(MainActivity.this, "Class not found", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(MainActivity.this, "Error fetching class details", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void enrollStudentInClass(String userId, String joinCode, String className, DocumentReference classEnrolledRef) {
        db.collection("users").document(userId).get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                String name = documentSnapshot.getString("name");
                String rollNumber = documentSnapshot.getString("rollNumber");
                classEnrolledRef.set(new HashMap<>()).addOnSuccessListener(aVoid -> {
                    Map<String, Object> studentInfo = new HashMap<>();
                    studentInfo.put("userId", userId);
                    studentInfo.put("email", user.getEmail());
                    studentInfo.put("name", name);
                    studentInfo.put("rollNumber", rollNumber);
                    db.collection("classes").document(joinCode).collection("students").document(userId).set(studentInfo).addOnSuccessListener(aVoid1 -> {
                        Toast.makeText(MainActivity.this, "Joined class successfully", Toast.LENGTH_SHORT).show();
                    }).addOnFailureListener(e -> {
                        Toast.makeText(MainActivity.this, "Failed to add student to class", Toast.LENGTH_SHORT).show();
                    });
                }).addOnFailureListener(e -> {
                    Toast.makeText(MainActivity.this, "Failed to join class", Toast.LENGTH_SHORT).show();
                });
            } else {
                Toast.makeText(MainActivity.this, "User details not found", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(MainActivity.this, "Failed to fetch user details", Toast.LENGTH_SHORT).show();
        });
    }

    private void fetchEnrolledSubjects() {
        if (user != null) {
            CollectionReference enrolledClassesRef = db.collection("users").document(user.getUid()).collection("classes_enrolled");
            enrolledClassesRef.get().addOnSuccessListener(queryDocumentSnapshots -> {
                List<String> enrolledSubjects = new ArrayList<>();
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    String subjectName = document.getString("className");
                    if (subjectName != null) {
                        enrolledSubjects.add(subjectName);
                    }
                }
                displayEnrolledSubjects(enrolledSubjects);
            }).addOnFailureListener(e -> {
                Log.e("FetchSubjects", "Error fetching enrolled subjects", e);
            });
        }
    }

    private void displayEnrolledSubjects(List<String> enrolledSubjects) {
        for (String subject : enrolledSubjects) {
            Log.d("EnrolledSubject", subject);
        }
    }

    private void createClass() {
        String className = classNameEditText.getText().toString().trim();
        if (className.isEmpty()) {
            Toast.makeText(this, "Class name is required", Toast.LENGTH_SHORT).show();
            return;
        }
        db.collection("classes").whereEqualTo("className", className).get().addOnSuccessListener(queryDocumentSnapshots -> {
            classNameEditText.setText("");
            classNameEditText.clearFocus();
            if (!queryDocumentSnapshots.isEmpty()) {
                Toast.makeText(MainActivity.this, "Class with this name already exists", Toast.LENGTH_SHORT).show();
            } else {
                createNewClass(className);
            }
        }).addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Failed to check for existing class", Toast.LENGTH_SHORT).show());
    }

    private void createNewClass(String className) {
        final String joinCode = generateRandomCode(6);
        boolean attendance = false;
        Map<String, Object> classInfo = new HashMap<>();
        classInfo.put("className", className);
        classInfo.put("joinCode", joinCode);
        classInfo.put("teacherEmail", user.getEmail());
        classInfo.put("attendance", attendance);
        db.collection("classes").document(joinCode).set(classInfo).addOnSuccessListener(aVoid -> {
            fetchClassesCreatedByTeacher();
            Toast.makeText(MainActivity.this, "Class created successfully", Toast.LENGTH_SHORT).show();
            fetchClassesCreatedByTeacher();
            getSubjectsList();
            updateTeacherClassesCreated(joinCode, className);
        }).addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Failed to create class", Toast.LENGTH_SHORT).show());
    }

    private void updateTeacherClassesCreated(String joinCode, String className) {
        Map<String, Object> classCreatedInfo = new HashMap<>();
        classCreatedInfo.put("className", className);
        classCreatedInfo.put("joinCode", joinCode);
        String teacherId = user.getUid();
        db.collection("users").document(teacherId).collection("classes_created").document(className).set(classCreatedInfo).addOnSuccessListener(aVoid -> Log.d("UpdateClassesCreated", "Classes created updated successfully")).addOnFailureListener(e -> Log.e("UpdateClassesCreated", "Error updating classes created", e));
    }

    private void updateStudentClassesEnrolled(String joinCode, String className) {
        String studentId = user.getUid();
        Map<String, Object> classEnrolledInfo = new HashMap<>();
        classEnrolledInfo.put("className", className);
        classEnrolledInfo.put("joinCode", joinCode);
        db.collection("users").document(studentId).collection("classes_enrolled").document(className).set(classEnrolledInfo).addOnSuccessListener(aVoid -> Log.d("UpdateClassesEnrolled", "Classes enrolled updated successfully")).addOnFailureListener(e -> Log.e("UpdateClassesEnrolled", "Error updating classes enrolled", e));
    }

    private String generateRandomCode(int length) {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder result = new StringBuilder();
        Random random = new Random();
        while (length-- > 0) {
            result.append(characters.charAt(random.nextInt(characters.length())));
        }
        return result.toString();
    }

    private void showJoinClassDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Join Class");
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);
        builder.setPositiveButton("Join", (dialog, which) -> joinClass(input.getText().toString().trim()));
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void fetchUserName() {
        if (user != null) {
            DocumentReference userRef = db.collection("users").document(user.getUid());
            userRef.get().addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    String userName = documentSnapshot.getString("name");
                    if (userName != null) {
                        facultyName.setText(userName);
                    }
                }
            }).addOnFailureListener(e -> {
                Toast.makeText(MainActivity.this, "Failed to fetch user's name", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void navigateToProfile() {
        Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
        intent.putExtra("email", user.getEmail());
        if ("student".equals(userRole)) {
            db.collection("users").document(user.getUid()).get().addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    String name = documentSnapshot.getString("name");
                    String rollNumber = documentSnapshot.getString("rollNumber");
                    String dob = documentSnapshot.getString("dob");
                    String phone = documentSnapshot.getString("phone");
                    String role = documentSnapshot.getString("role");
                    intent.putExtra("name", name);
                    intent.putExtra("rollNumber", rollNumber);
                    intent.putExtra("dob", dob);
                    intent.putExtra("phone", phone);
                    intent.putExtra("role", role);
                    startActivity(intent);
                }
            }).addOnFailureListener(e -> {
            });
        } else {
            db.collection("users").document(user.getUid()).get().addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    String name = documentSnapshot.getString("name");
                    String dob = documentSnapshot.getString("dob");
                    String phone = documentSnapshot.getString("phone");
                    String role = documentSnapshot.getString("role");
                    intent.putExtra("name", name);
                    intent.putExtra("dob", dob);
                    intent.putExtra("phone", phone);
                    intent.putExtra("role", role);
                    startActivity(intent);
                }
            }).addOnFailureListener(e -> {
            });
            startActivity(intent);
        }
    }

    private boolean isDeveloperModeEnabled() {
        return Settings.Secure.getInt(getApplicationContext().getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1;
    }
}