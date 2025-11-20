package com.example.vid2;

import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

@UnstableApi
public class PlayerActivity extends AppCompatActivity {

    private PlayerView playerView;
    private ExoPlayer exoPlayer;

    private ImageButton btnPlayPause, btnForward, btnRewind, btnNext, btnPrev,
            btnSpeed, btnSubtitle, btnLoadSubtitle, btnVolume;
    private SeekBar seekBar, volumeSeekBar;
    private TextView txtCurrentTime, txtTotalTime, txtSpeed, txtVolume, txtVideoTitle;
    private SubtitleView subtitleView;
    private View volumeLayout, controlLayout;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private AudioManager audioManager;

    private ArrayList<String> videoList = new ArrayList<>();
    private int currentIndex = 0;
    private float currentSpeed = 1.0f;

    private boolean subtitlesEnabled = true;
    private boolean loadSubtitles = true;
    private boolean controlsVisible = true;
    private boolean volumeVisible = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        // Bind views
        playerView       = findViewById(R.id.playerView);
        subtitleView     = findViewById(R.id.subtitleView);
        btnPlayPause     = findViewById(R.id.btnPlayPause);
        btnForward       = findViewById(R.id.btnForward);
        btnRewind        = findViewById(R.id.btnRewind);
        btnNext          = findViewById(R.id.btnNext);
        btnPrev          = findViewById(R.id.btnPrev);
        btnSpeed         = findViewById(R.id.btnSpeed);
        btnSubtitle      = findViewById(R.id.btnSubtitle);
        btnLoadSubtitle  = findViewById(R.id.btnLoadSubtitle);
        btnVolume        = findViewById(R.id.btnVolume);
        seekBar          = findViewById(R.id.seekBar);
        txtCurrentTime   = findViewById(R.id.txtCurrentTime);
        txtTotalTime     = findViewById(R.id.txtTotalTime);
        txtSpeed         = findViewById(R.id.txtSpeed);
        txtVolume        = findViewById(R.id.txtVolume);
        txtVideoTitle    = findViewById(R.id.txtVideoTitle);
        volumeSeekBar    = findViewById(R.id.volumeSeekBar);
        volumeLayout     = findViewById(R.id.volumeLayout);
        controlLayout    = findViewById(R.id.controlLayout);

        // Get video list
        ArrayList<String> passed = getIntent().getStringArrayListExtra("videoList");
        if (passed != null) videoList = passed;
        currentIndex = getIntent().getIntExtra("currentIndex", 0);
        if (videoList.isEmpty()) {
            Toast.makeText(this, "No videos provided", Toast.LENGTH_LONG).show();
        }

        // Initialize player
        exoPlayer = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(exoPlayer);

        // Set video title
        if (currentIndex < videoList.size()) {
            File videoFile = new File(videoList.get(currentIndex));
            txtVideoTitle.setText(videoFile.getName());
        }

        // Player listeners
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                Toast.makeText(PlayerActivity.this,
                        "Playback error: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    updateTotalDuration();
                }
            }

            @Override
            public void onMediaItemTransition(androidx.media3.common.MediaItem mediaItem, int reason) {
                if (mediaItem != null) {
                    int index = exoPlayer.getCurrentMediaItemIndex();
                    if (index < videoList.size()) {
                        File videoFile = new File(videoList.get(index));
                        txtVideoTitle.setText(videoFile.getName());
                    }
                }
            }
        });

        // Setup initial state
        updatePlayPauseIcon();
        subtitleView.setVisibility(subtitlesEnabled ? View.VISIBLE : View.GONE);

        // Setup playlist and start
        setupPlaylist();
        exoPlayer.prepare();
        exoPlayer.play();

        // Play/Pause button
        btnPlayPause.setOnClickListener(v -> {
            if (exoPlayer.isPlaying()) {
                exoPlayer.pause();
            } else {
                exoPlayer.play();
            }
            updatePlayPauseIcon();
        });

        // Forward 10s
        btnForward.setOnClickListener(v ->
                exoPlayer.seekTo(exoPlayer.getCurrentPosition() + 10_000));

        // Rewind 10s
        btnRewind.setOnClickListener(v ->
                exoPlayer.seekTo(Math.max(exoPlayer.getCurrentPosition() - 10_000, 0)));

        // Next video
        btnNext.setOnClickListener(v -> {
            if (exoPlayer.hasNextMediaItem()) {
                exoPlayer.seekToNextMediaItem();
            } else {
                Toast.makeText(this, "No next video", Toast.LENGTH_SHORT).show();
            }
        });

        // Previous video
        btnPrev.setOnClickListener(v -> {
            if (exoPlayer.hasPreviousMediaItem()) {
                exoPlayer.seekToPreviousMediaItem();
            } else {
                Toast.makeText(this, "No previous video", Toast.LENGTH_SHORT).show();
            }
        });

        // Speed control
        btnSpeed.setOnClickListener(v -> {
            if (currentSpeed == 1.0f) currentSpeed = 1.5f;
            else if (currentSpeed == 1.5f) currentSpeed = 2.0f;
            else if (currentSpeed == 2.0f) currentSpeed = 0.5f;
            else if (currentSpeed == 0.5f) currentSpeed = 0.75f;
            else currentSpeed = 1.0f;

            exoPlayer.setPlaybackSpeed(currentSpeed);
            txtSpeed.setText(String.format("%.1fx", currentSpeed));
        });

        // Subtitle visibility toggle
        btnSubtitle.setOnClickListener(v -> {
            subtitlesEnabled = !subtitlesEnabled;
            subtitleView.setVisibility(subtitlesEnabled ? View.VISIBLE : View.GONE);
        });

        // Subtitle loading toggle
        btnLoadSubtitle.setOnClickListener(v -> {
            loadSubtitles = !loadSubtitles;
            rebuildPlaylistKeepingCurrentIndex();
            Toast.makeText(this,
                    loadSubtitles ? "Subtitles enabled" : "Subtitles disabled",
                    Toast.LENGTH_SHORT).show();
        });

        // Volume toggle
        btnVolume.setOnClickListener(v -> {
            volumeVisible = !volumeVisible;
            volumeLayout.setVisibility(volumeVisible ? View.VISIBLE : View.GONE);
        });

        // SeekBar for video progress
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser && exoPlayer.getDuration() > 0) {
                    long posMs = progress * exoPlayer.getDuration() / 1000L;
                    txtCurrentTime.setText(formatMs(posMs));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                handler.removeCallbacksAndMessages(null);
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                if (exoPlayer.getDuration() > 0) {
                    long posMs = bar.getProgress() * exoPlayer.getDuration() / 1000L;
                    exoPlayer.seekTo(posMs);
                }
                startProgressUpdater();
            }
        });

        startProgressUpdater();

        // Volume control
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager != null) {
            int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            volumeSeekBar.setMax(max);
            volumeSeekBar.setProgress(cur);
            updateVolumeText(cur, max);

            volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
                        updateVolumeText(progress, seekBar.getMax());
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) { }
                @Override public void onStopTrackingTouch(SeekBar seekBar) { }
            });
        }

        // Touch to show/hide controls
        playerView.setOnClickListener(v -> toggleControls());
    }

    private void updatePlayPauseIcon() {
        if (exoPlayer.isPlaying()) {
            btnPlayPause.setImageResource(R.drawable.ic_pause);
        } else {
            btnPlayPause.setImageResource(R.drawable.ic_play);
        }
    }

    private void toggleControls() {
        controlsVisible = !controlsVisible;
        controlLayout.setVisibility(controlsVisible ? View.VISIBLE : View.GONE);
        txtVideoTitle.setVisibility(controlsVisible ? View.VISIBLE : View.GONE);
    }

    private void updateVolumeText(int current, int max) {
        int percentage = (int) ((current / (float) max) * 100);
        txtVolume.setText(percentage + "%");
    }

    private void setupPlaylist() {
        exoPlayer.clearMediaItems();
        for (String path : videoList) {
            MediaItem mediaItem = buildMediaItemWithOptionalSubtitle(path);
            exoPlayer.addMediaItem(mediaItem);
        }
        if (currentIndex >= 0 && currentIndex < exoPlayer.getMediaItemCount()) {
            exoPlayer.seekTo(currentIndex, 0);
        }
    }

    private void rebuildPlaylistKeepingCurrentIndex() {
        int index = exoPlayer.getCurrentMediaItemIndex();
        long pos = exoPlayer.getCurrentPosition();
        setupPlaylist();
        exoPlayer.prepare();
        if (index >= 0 && index < exoPlayer.getMediaItemCount()) {
            exoPlayer.seekTo(index, pos);
        }
        if (exoPlayer.isPlaying()) exoPlayer.play();
    }

    private MediaItem buildMediaItemWithOptionalSubtitle(String videoPath) {
        MediaItem.Builder builder = new MediaItem.Builder()
                .setUri(Uri.fromFile(new File(videoPath)));

        if (loadSubtitles) {
            File srt = guessSubtitleFile(videoPath);
            if (srt != null && srt.exists()) {
                MediaItem.SubtitleConfiguration subtitleConfig =
                        new MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(srt))
                                .setMimeType("application/x-subrip")
                                .setLanguage("en")
                                .setSelectionFlags(0)
                                .build();
                builder.setSubtitleConfigurations(
                        java.util.Collections.singletonList(subtitleConfig));
            }
        }
        return builder.build();
    }

    private File guessSubtitleFile(String videoPath) {
        File videoFile = new File(videoPath);
        String nameNoExt = videoFile.getName();
        int dot = nameNoExt.lastIndexOf('.');
        if (dot > 0) nameNoExt = nameNoExt.substring(0, dot);
        return new File(videoFile.getParentFile(), nameNoExt + ".srt");
    }

    private void startProgressUpdater() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (exoPlayer != null) {
                    long pos = exoPlayer.getCurrentPosition();
                    long dur = exoPlayer.getDuration();
                    txtCurrentTime.setText(formatMs(pos));
                    if (dur > 0) {
                        txtTotalTime.setText(formatMs(dur));
                        int progress = (int) (pos * 1000L / dur);
                        seekBar.setProgress(progress);
                    } else {
                        txtTotalTime.setText("00:00");
                        seekBar.setProgress(0);
                    }
                }
                handler.postDelayed(this, 500);
            }
        }, 500);
    }

    private void updateTotalDuration() {
        long dur = exoPlayer.getDuration();
        txtTotalTime.setText(formatMs(dur));
    }

    private String formatMs(long ms) {
        long totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms);
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        else return String.format("%02d:%02d", m, s);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
    }
}