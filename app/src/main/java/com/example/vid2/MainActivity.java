package com.example.vid2;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private RecyclerView videoRecyclerView;
    private ArrayList<String> videoList;
    private ArrayList<String> filteredVideoList;
    private VideoAdapter adapter;
    private EditText searchBar;
    private TextView videoCountText;
    private static final int REQUEST_PERMISSION = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_homepage);

        videoRecyclerView = findViewById(R.id.videoRecyclerView);
        searchBar = findViewById(R.id.searchBar);
        videoCountText = findViewById(R.id.videoCountText);

        videoRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Initialize lists
        videoList = new ArrayList<>();
        filteredVideoList = new ArrayList<>();

        // Check permissions
        checkPermissions();

        // Setup search functionality
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterVideos(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void checkPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_MEDIA_VIDEO}, REQUEST_PERMISSION);
            } else {
                loadVideos();
            }
        } else {
            // Android 12 and below
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_PERMISSION);
            } else {
                loadVideos();
            }
        }
    }

    private void loadVideos() {
        videoList.clear();
        filteredVideoList.clear();

        Uri uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {MediaStore.Video.Media.DATA};
        String sortOrder = MediaStore.Video.Media.DATE_MODIFIED + " DESC";

        Cursor cursor = getContentResolver().query(uri, projection, null, null, sortOrder);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA));
                videoList.add(path);
                filteredVideoList.add(path);
            }
            cursor.close();
        }

        // Update video count
        updateVideoCount(filteredVideoList.size());

        // Set adapter
        adapter = new VideoAdapter(filteredVideoList, this);
        videoRecyclerView.setAdapter(adapter);

        if (videoList.isEmpty()) {
            Toast.makeText(this, "No videos found on device", Toast.LENGTH_LONG).show();
        }
    }

    private void filterVideos(String query) {
        filteredVideoList.clear();

        if (query.isEmpty()) {
            filteredVideoList.addAll(videoList);
        } else {
            String lowerCaseQuery = query.toLowerCase();
            for (String videoPath : videoList) {
                String fileName = new java.io.File(videoPath).getName().toLowerCase();
                if (fileName.contains(lowerCaseQuery)) {
                    filteredVideoList.add(videoPath);
                }
            }
        }

        updateVideoCount(filteredVideoList.size());

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void updateVideoCount(int count) {
        if (count == 1) {
            videoCountText.setText("1 video");
        } else {
            videoCountText.setText(count + " videos");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadVideos();
            } else {
                Toast.makeText(this, "Permission denied! Cannot access videos.", Toast.LENGTH_LONG).show();
            }
        }
    }
}