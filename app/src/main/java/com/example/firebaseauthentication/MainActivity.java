package com.example.firebaseauthentication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    TextView textView;
    Button logoutButton, createClassButton, joinClassButton, refreshButton;
    EditText classNameEditText;
    FirebaseAuth auth;
    FirebaseUser user;
    FirebaseFirestore db;
    private RecyclerView subjectsRecyclerView;
    private String userRole;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.user_details);
        logoutButton = findViewById(R.id.logout);
        createClassButton = findViewById(R.id.createClassButton);
        joinClassButton = findViewById(R.id.joinClassButton);
        classNameEditText = findViewById(R.id.classNameEditText);
        subjectsRecyclerView = findViewById(R.id.subjectsRecyclerView);
        SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                getSubjectsList();
                swipeRefreshLayout.setRefreshing(false); // To stop the refresh animation
            }
        });
        auth = FirebaseAuth.getInstance();
        user = auth.getCurrentUser();
        db = FirebaseFirestore.getInstance();
        getSubjectsList();
        setupRecyclerView();
        if (user == null) {
            navigateToLogin();
        } else {
            textView.setText(user.getEmail());
            checkUserRole();
            setupRecyclerView();
            fetchEnrolledSubjects(); // Call the method here
        }
        setupButtonListeners();
    }

    private void setupRecyclerView() {
        subjectsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        // Initialize with an empty adapter
        SubjectAdapter adapter = new SubjectAdapter(new ArrayList<>(), this::onDeleteSubject);
        subjectsRecyclerView.setAdapter(adapter);
        // Now fetch the subjects and update the adapter
        getSubjectsList();
    }

    private void onDeleteSubject(String className) {
        // Show confirmation dialog before deleting
        showDeleteConfirmationDialog(className, () -> deleteSubject(className));
    }

    private void showDeleteConfirmationDialog(String className, Runnable deleteCallback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Confirm Deletion");
        builder.setMessage("Are you sure you want to delete " + className + "?");

        builder.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                deleteCallback.run(); // Perform the deletion
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void deleteSubject(String className) {
        // First, delete the class from the 'classes' collection
        db.collection("classes").whereEqualTo("className", className).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                for (DocumentSnapshot document : task.getResult()) {
                    String classId = document.getId();

                    // Delete the class from 'classes_created' for the teacher
                    if (user != null) {
                        db.collection("users").document(user.getUid()).collection("classes_created").document(classId).delete();
                    }

                    // Delete the class from 'classes_enrolled' for all enrolled students
                    db.collection("classes").document(classId).collection("students").get()
                            .addOnCompleteListener(task1 -> {
                                if (task1.isSuccessful()) {
                                    for (DocumentSnapshot studentDoc : task1.getResult()) {
                                        String studentId = studentDoc.getId();
                                        db.collection("users").document(studentId).collection("classes_enrolled").document(className).delete();
                                    }
                                }
                            });

                    // Finally, delete the class document itself
                    db.collection("classes").document(classId).delete().addOnSuccessListener(aVoid -> {
                        Toast.makeText(MainActivity.this, "Class deleted", Toast.LENGTH_SHORT).show();
                        // Refresh your list here, if necessary
                    }).addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Error deleting class", Toast.LENGTH_SHORT).show());
                }
            } else {
                Toast.makeText(MainActivity.this, "Error finding class", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void getSubjectsList() {
        db.collection("classes")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        List<Map<String, String>> classes = new ArrayList<>();
                        for (DocumentSnapshot document : task.getResult()) {
                            Map<String, String> classInfo = new HashMap<>();
                            String className = document.getString("className");
                            String joinCode = document.getString("joinCode");
                            if (className != null && joinCode != null) {
                                classInfo.put("className", className);
                                classInfo.put("joinCode", joinCode);
                                classes.add(classInfo);
                            }
                        }
                        // Update adapter here
                        SubjectAdapter adapter = new SubjectAdapter(classes, this::onDeleteSubject);
                        subjectsRecyclerView.setAdapter(adapter);
                    } else {
                        Toast.makeText(MainActivity.this, "Error loading classes", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void setupButtonListeners() {
        logoutButton.setOnClickListener(v -> logoutUser());
        createClassButton.setOnClickListener(v -> createClass());
        joinClassButton.setOnClickListener(v -> showJoinClassDialog());
    }

    private void logoutUser() {
        new AlertDialog.Builder(this).setTitle("Logout").setMessage("Are you sure you want to logout?").setPositiveButton("Logout", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                auth.signOut();
                navigateToLogin();
            }
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
            getSubjectsList(); // Fetch subjects after knowing the role
        }
    }

    private void updateUIForRole(String role) {
        if ("teacher".equals(role)) {
            createClassButton.setVisibility(View.VISIBLE);
            joinClassButton.setVisibility(View.GONE);
        } else if ("student".equals(role)) {
            joinClassButton.setVisibility(View.VISIBLE);
            createClassButton.setVisibility(View.GONE);
        }
    }

    private void joinClass(final String joinCode) {
        if (joinCode.isEmpty()) {
            Toast.makeText(MainActivity.this, "Join code is required", Toast.LENGTH_SHORT).show();
            return;
        }
        // Check if class exists with the given join code
        db.collection("classes").document(joinCode).get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (task.isSuccessful()) {
                    DocumentSnapshot document = task.getResult();
                    if (document.exists()) {
                        // Class exists, proceed to add student
                        addUserToClass(joinCode);
                    } else {
                        Toast.makeText(MainActivity.this, "Invalid join code", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Error checking join code", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void addUserToClass(final String joinCode) {
        final String userId = user.getUid(); // Get current user's ID
        // Fetch the class details using the join code
        db.collection("classes").document(joinCode).get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (task.isSuccessful()) {
                    DocumentSnapshot classDoc = task.getResult();
                    if (classDoc.exists()) {
                        // Extract the class name
                        final String className = classDoc.getString("className");
                        // Check if the student is already enrolled in the class
                        final DocumentReference classEnrolledRef = db.collection("users").document(userId).collection("classes_enrolled").document(className);
                        classEnrolledRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                            @Override
                            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                if (task.isSuccessful()) {
                                    DocumentSnapshot document = task.getResult();
                                    if (document.exists()) {
                                        Toast.makeText(MainActivity.this, "Already enrolled in class", Toast.LENGTH_SHORT).show();
                                    } else {
                                        // Student not enrolled in class, proceed to enroll
                                        enrollStudentInClass(userId, joinCode, className, classEnrolledRef);
                                    }
                                } else {
                                    Toast.makeText(MainActivity.this, "Failed to check class enrollment", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    } else {
                        Toast.makeText(MainActivity.this, "Class not found", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Error fetching class details", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void enrollStudentInClass(String userId, String joinCode, String className, DocumentReference classEnrolledRef) {
        // Create an empty document in the 'classes_enrolled' collection
        classEnrolledRef.set(new HashMap<>()) // Use an empty HashMap
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Toast.makeText(MainActivity.this, "Joined class successfully", Toast.LENGTH_SHORT).show();
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(MainActivity.this, "Failed to join class", Toast.LENGTH_SHORT).show();
                    }
                });
        // Add student to a sub-collection in the class document
        Map<String, Object> studentInfo = new HashMap<>();
        studentInfo.put("userId", userId);
        studentInfo.put("email", user.getEmail());
        db.collection("classes").document(joinCode).collection("students").document(userId).set(studentInfo).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                // This can be kept empty if you don't need to do anything here
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(MainActivity.this, "Failed to add student to class", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchEnrolledSubjects() {
        if (user != null) {
            // Get the reference to the "classes_enrolled" collection for the current user
            CollectionReference enrolledClassesRef = db.collection("users").document(user.getUid()).collection("classes_enrolled");

            // Fetch the documents from the "classes_enrolled" collection
            enrolledClassesRef.get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        List<String> enrolledSubjects = new ArrayList<>();
                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            // Get the subject name from each document
                            String subjectName = document.getString("className");
                            if (subjectName != null) {
                                // Add the subject name to the list of enrolled subjects
                                enrolledSubjects.add(subjectName);
                            }
                        }
                        // Do something with the list of enrolled subjects (e.g., display in UI)
                        displayEnrolledSubjects(enrolledSubjects);
                    })
                    .addOnFailureListener(e -> {
                        // Handle any errors that may occur during fetching
                        Log.e("FetchSubjects", "Error fetching enrolled subjects", e);
                    });
        }
    }

    private void displayEnrolledSubjects(List<String> enrolledSubjects) {
        // Here, you can display the enrolled subjects in the UI or perform any other operations
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

        // Check if a class with the same name already exists
        db.collection("classes").whereEqualTo("className", className).get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        // Class with same name already exists
                        Toast.makeText(MainActivity.this, "Class with this name already exists", Toast.LENGTH_SHORT).show();
                    } else {
                        // No class with the same name, proceed to create new class
                        createNewClass(className);
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Failed to check for existing class", Toast.LENGTH_SHORT).show());
    }

    private void createNewClass(String className) {
        final String joinCode = generateRandomCode(6);

        Map<String, Object> classInfo = new HashMap<>();
        classInfo.put("className", className);
        classInfo.put("joinCode", joinCode);
        classInfo.put("teacherEmail", user.getEmail()); // Add the teacher's email

        // Create or update class in classes collection
        db.collection("classes").document(joinCode).set(classInfo)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(MainActivity.this, "Class created successfully", Toast.LENGTH_SHORT).show();
                    // Update the teacher's 'classes_created' sub-collection
                    updateTeacherClassesCreated(joinCode, className);
                })
                .addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Failed to create class", Toast.LENGTH_SHORT).show());
    }

    private void updateTeacherClassesCreated(String joinCode, String className) {
        Map<String, Object> classCreatedInfo = new HashMap<>();
        classCreatedInfo.put("className", className);
        classCreatedInfo.put("joinCode", joinCode);

        // Assuming 'user' is the current logged-in teacher
        String teacherId = user.getUid(); // Get the teacher's UID

        // Add the class to the teacher's 'classes_created' sub-collection
        db.collection("users").document(teacherId).collection("classes_created").document(joinCode).set(classCreatedInfo)
                .addOnSuccessListener(aVoid -> Log.d("UpdateClassesCreated", "Classes created updated successfully"))
                .addOnFailureListener(e -> Log.e("UpdateClassesCreated", "Error updating classes created", e));
    }

    private void updateStudentClassesEnrolled(String joinCode, String className) {
        Map<String, Object> classEnrolledInfo = new HashMap<>();
        classEnrolledInfo.put("className", className);
        classEnrolledInfo.put("joinCode", joinCode);

        // Get the student's UID
        String studentId = user.getUid();

        // Add the class to the student's 'classes_enrolled' sub-collection
        db.collection("users").document(studentId).collection("classes_enrolled").document(joinCode).set(classEnrolledInfo)
                .addOnSuccessListener(aVoid -> Log.d("UpdateClassesEnrolled", "Classes enrolled updated successfully"))
                .addOnFailureListener(e -> Log.e("UpdateClassesEnrolled", "Error updating classes enrolled", e));
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
        builder.setPositiveButton("Join", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                joinClass(input.getText().toString().trim());
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }
}