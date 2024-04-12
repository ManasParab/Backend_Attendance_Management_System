package com.example.firebaseauthentication;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Map;

public class SubjectAdapter extends RecyclerView.Adapter<SubjectAdapter.SubjectViewHolder> {

    private List<Map<String, String>> subjects;
    private OnSubjectDeleteListener deleteListener;

    public SubjectAdapter(List<Map<String, String>> subjects, OnSubjectDeleteListener deleteListener) {
        this.subjects = subjects;
        this.deleteListener = deleteListener;
    }

    @NonNull
    @Override
    public SubjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_subject, parent, false);
        return new SubjectViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SubjectViewHolder holder, int position) {
        Map<String, String> subject = subjects.get(position);
        holder.tvSubjectName.setText(subject.get("className"));
        holder.btnDelete.setOnClickListener(v -> deleteListener.onDelete(subject.get("className")));

        // Set the item click listener
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), SubjectDetailsActivity.class);
            intent.putExtra("subjectName", subject.get("className"));
            intent.putExtra("joinCode", subject.get("joinCode"));
            v.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return subjects != null ? subjects.size() : 0;
    }

    interface OnSubjectDeleteListener {
        void onDelete(String subjectName);
    }

    public static class SubjectViewHolder extends RecyclerView.ViewHolder {

        TextView tvSubjectName;
        TextView tvJoinCode;
        Button btnDelete;

        SubjectViewHolder(View itemView) {
            super(itemView);
            tvSubjectName = itemView.findViewById(R.id.tvSubjectName);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}