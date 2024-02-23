package com.example.firebaseauthentication;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TeacherActivity extends AppCompatActivity implements SubjectAdapter.OnSubjectDeleteListener {

    private static final String TAG = "TeacherActivity";
    private RecyclerView recyclerView;
    private SubjectAdapter adapter;
    private List<Map<String, String>> subjects;
    private FirebaseFirestore firestore;
    private CollectionReference subjectsCollection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.subjectsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        subjects = new ArrayList<>();
        firestore = FirebaseFirestore.getInstance();
        subjectsCollection = firestore.collection("subjects");

        fetchSubjects();

        adapter = new SubjectAdapter(subjects, this);
        recyclerView.setAdapter(adapter);
    }

    private void fetchSubjects() {
        subjectsCollection.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                QuerySnapshot querySnapshot = task.getResult();
                if (querySnapshot != null) {
                    for (DocumentSnapshot document : querySnapshot) {
                        Map<String, String> subject = new HashMap<>();
                        subject.put("className", document.getString("name"));
                        subject.put("joinCode", document.getString("joinCode")); // Assuming there's a field "joinCode"
                        subjects.add(subject);
                    }
                    adapter.notifyDataSetChanged();
                }
            } else {
                Log.w(TAG, "Error getting documents.", task.getException());
            }
        });
    }

    @Override
    public void onDelete(String subjectName) {
        deleteSubject(subjectName);
    }


    private void deleteSubject(String subjectName) {
        subjectsCollection.whereEqualTo("name", subjectName)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        for (DocumentSnapshot document : task.getResult()) {
                            subjectsCollection.document(document.getId()).delete()
                                    .addOnSuccessListener(aVoid -> {
                                        // Remove the subject from the list
                                        for (int i = 0; i < subjects.size(); i++) {
                                            if (subjects.get(i).get("className").equals(subjectName)) {
                                                subjects.remove(i);
                                                adapter.notifyItemRemoved(i);
                                                break;
                                            }
                                        }
                                        Log.d(TAG, "DocumentSnapshot successfully deleted!");
                                    })
                                    .addOnFailureListener(e -> Log.w(TAG, "Error deleting document", e));
                        }
                    } else {
                        Log.d(TAG, "Error getting documents: ", task.getException());
                    }
                });
    }
}