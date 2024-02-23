package com.example.firebaseauthentication;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class SubjectDetailsActivity extends AppCompatActivity {

    private TextView tvSubjectName, tvJoiningCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_details);

        tvSubjectName = findViewById(R.id.tvSubjectName);
        tvJoiningCode = findViewById(R.id.tvJoiningCode);

        // Retrieve data from intent
        String subjectName = getIntent().getStringExtra("subjectName");
        String joiningCode = getIntent().getStringExtra("joinCode");

        // Set data to views
        tvSubjectName.setText(subjectName);
        tvJoiningCode.setText(joiningCode);
    }
}