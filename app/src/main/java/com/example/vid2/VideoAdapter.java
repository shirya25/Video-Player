package com.example.vid2;

import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {

    private ArrayList<String> videoList;
    private Context context;

    public VideoAdapter(ArrayList<String> videoList, Context context) {
        this.videoList = videoList;
        this.context = context;
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.video_item, parent, false);
        return new VideoViewHolder(view);
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        String path = videoList.get(position);
        File file = new File(path);

        // Set video name
        holder.videoName.setText(file.getName());

        // Load thumbnail using Glide
        Glide.with(context)
                .load(Uri.fromFile(file))
                .thumbnail(0.1f)
                .placeholder(R.drawable.ic_play_circle)
                .into(holder.videoThumbnail);

        // Get video metadata
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(path);

            // Duration
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durationStr != null) {
                long duration = Long.parseLong(durationStr);
                holder.videoDuration.setText(formatDuration(duration));
            }

            retriever.release();
        } catch (Exception e) {
            holder.videoDuration.setText("--:--");
        }

        // File size
        long sizeInBytes = file.length();
        String size = formatFileSize(sizeInBytes);
        holder.videoSize.setText(size);

        // Last modified date
        long lastModified = file.lastModified();
        String date = formatDate(lastModified);
        holder.videoDate.setText(date);

        // Handle click: open PlayerActivity
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, PlayerActivity.class);
            intent.putExtra("videoList", videoList);
            intent.putExtra("currentIndex", position);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return videoList.size();
    }

    private String formatDuration(long milliseconds) {
        long seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, secs);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, secs);
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    private String formatDate(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        if (diff < 24 * 60 * 60 * 1000) {
            return "Today";
        } else if (diff < 48 * 60 * 60 * 1000) {
            return "Yesterday";
        } else if (diff < 7 * 24 * 60 * 60 * 1000) {
            long days = diff / (24 * 60 * 60 * 1000);
            return days + " days ago";
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }
    }

    public static class VideoViewHolder extends RecyclerView.ViewHolder {
        TextView videoName, videoSize, videoDate, videoDuration;
        ImageView videoThumbnail;

        public VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            videoName = itemView.findViewById(R.id.videoName);
            videoSize = itemView.findViewById(R.id.videoSize);
            videoDate = itemView.findViewById(R.id.videoDate);
            videoDuration = itemView.findViewById(R.id.videoDuration);
            videoThumbnail = itemView.findViewById(R.id.videoThumbnail);
        }
    }
}