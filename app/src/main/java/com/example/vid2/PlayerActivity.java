package com.example.vid2;

import android.app.AlertDialog;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@UnstableApi
public class PlayerActivity extends AppCompatActivity {

    private static final String TAG = "PlayerActivity";

    private PlayerView playerView;
    private ExoPlayer exoPlayer;
    private DefaultTrackSelector trackSelector;

    private ImageButton btnPlayPause, btnForward, btnRewind, btnNext, btnPrev,
            btnSpeed, btnSubtitle, btnLoadSubtitle, btnVolume, btnAspectRatio, btnAudioTrack;
    private SeekBar seekBar, volumeSeekBar;
    private TextView txtCurrentTime, txtTotalTime, txtSpeed, txtVolume, txtVideoTitle;
    private SubtitleView subtitleView;
    private View volumeLayout, controlLayout, topRightControls;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private AudioManager audioManager;

    private ArrayList<String> videoList = new ArrayList<>();
    private int currentIndex = 0;
    private float currentSpeed = 1.0f;

    private boolean subtitlesEnabled = true;
    private boolean loadSubtitles = true;
    private boolean controlsVisible = true;
    private boolean volumeVisible = false;
    private boolean isFullScreenMode = false;

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
        btnAspectRatio   = findViewById(R.id.btnAspectRatio);
        btnAudioTrack    = findViewById(R.id.btnAudioTrack);
        seekBar          = findViewById(R.id.seekBar);
        txtCurrentTime   = findViewById(R.id.txtCurrentTime);
        txtTotalTime     = findViewById(R.id.txtTotalTime);
        txtSpeed         = findViewById(R.id.txtSpeed);
        txtVolume        = findViewById(R.id.txtVolume);
        txtVideoTitle    = findViewById(R.id.txtVideoTitle);
        volumeSeekBar    = findViewById(R.id.volumeSeekBar);
        volumeLayout     = findViewById(R.id.volumeLayout);
        controlLayout    = findViewById(R.id.controlLayout);
        topRightControls = findViewById(R.id.topRightControls);

        // Get video list from intent
        ArrayList<String> passed = getIntent().getStringArrayListExtra("videoList");
        if (passed != null) videoList = passed;
        currentIndex = getIntent().getIntExtra("currentIndex", 0);
        if (videoList.isEmpty()) {
            Toast.makeText(this, "No videos provided", Toast.LENGTH_LONG).show();
        }

        // Initialize ExoPlayer
        trackSelector = new DefaultTrackSelector(this);
        exoPlayer = new ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .setMediaSourceFactory(
                        new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this)
                                .setDataSourceFactory(
                                        new androidx.media3.datasource.DefaultDataSourceFactory(this)
                                )
                )
                .build();
        playerView.setPlayer(exoPlayer);

        // Set video title from currentIndex if available
        if (currentIndex < videoList.size()) {
            File videoFile = new File(videoList.get(currentIndex));
            txtVideoTitle.setText(videoFile.getName());
        }

        // Player listeners
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                String errorMsg = "Playback error: ";
                if (error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED) {
                    errorMsg += "Format not supported on this device";
                } else if (error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED) {
                    errorMsg += "Video format not supported";
                } else {
                    errorMsg += error.getMessage();
                }
                Toast.makeText(PlayerActivity.this, errorMsg, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    updateTotalDuration();
                    updatePlayPauseIcon();
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updatePlayPauseIcon();
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

        // Subtitle visibility
        subtitleView.setVisibility(subtitlesEnabled ? View.VISIBLE : View.GONE);

        // Setup playlist and start
        setupPlaylist();
        exoPlayer.prepare();
        exoPlayer.play();

        // Controls listeners (using exoPlayer directly)
        btnPlayPause.setOnClickListener(v -> {
            if (exoPlayer.isPlaying()) exoPlayer.pause();
            else exoPlayer.play();
            updatePlayPauseIcon();
        });

        btnForward.setOnClickListener(v -> exoPlayer.seekTo(exoPlayer.getCurrentPosition() + 10_000));
        btnRewind.setOnClickListener(v -> exoPlayer.seekTo(Math.max(exoPlayer.getCurrentPosition() - 10_000, 0)));

        btnNext.setOnClickListener(v -> {
            if (exoPlayer.hasNextMediaItem()) exoPlayer.seekToNextMediaItem();
            else Toast.makeText(this, "No next video", Toast.LENGTH_SHORT).show();
        });

        btnPrev.setOnClickListener(v -> {
            if (exoPlayer.hasPreviousMediaItem()) exoPlayer.seekToPreviousMediaItem();
            else Toast.makeText(this, "No previous video", Toast.LENGTH_SHORT).show();
        });

        btnSpeed.setOnClickListener(v -> {
            if (currentSpeed == 1.0f) currentSpeed = 1.5f;
            else if (currentSpeed == 1.5f) currentSpeed = 2.0f;
            else if (currentSpeed == 2.0f) currentSpeed = 0.5f;
            else if (currentSpeed == 0.5f) currentSpeed = 0.75f;
            else currentSpeed = 1.0f;

            exoPlayer.setPlaybackSpeed(currentSpeed);
            txtSpeed.setText(String.format("%.1fx", currentSpeed));
        });

        btnSubtitle.setOnClickListener(v -> {
            subtitlesEnabled = !subtitlesEnabled;
            subtitleView.setVisibility(subtitlesEnabled ? View.VISIBLE : View.GONE);
        });

        btnLoadSubtitle.setOnClickListener(v -> {
            loadSubtitles = !loadSubtitles;
            rebuildPlaylistKeepingCurrentIndex();
            Toast.makeText(this, loadSubtitles ? "Subtitles enabled" : "Subtitles disabled", Toast.LENGTH_SHORT).show();
        });

        btnVolume.setOnClickListener(v -> {
            volumeVisible = !volumeVisible;
            volumeLayout.setVisibility(volumeVisible ? View.VISIBLE : View.GONE);
        });

        btnAspectRatio.setOnClickListener(v -> {
            isFullScreenMode = !isFullScreenMode;
            if (isFullScreenMode) {
                playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
                btnAspectRatio.setImageResource(R.drawable.ic_fullscreen_exit);
                Toast.makeText(this, "Fill Screen", Toast.LENGTH_SHORT).show();
            } else {
                playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
                btnAspectRatio.setImageResource(R.drawable.ic_fullscreen);
                Toast.makeText(this, "Fit Screen", Toast.LENGTH_SHORT).show();
            }
        });

        btnAudioTrack.setOnClickListener(v -> showAudioTrackDialog());

        // SeekBar
        seekBar.setMax(1000);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser && exoPlayer.getDuration() > 0) {
                    long posMs = (long) progress * exoPlayer.getDuration() / 1000L;
                    txtCurrentTime.setText(formatMs(posMs));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { handler.removeCallbacksAndMessages(null); }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                if (exoPlayer.getDuration() > 0) {
                    long posMs = (long) bar.getProgress() * exoPlayer.getDuration() / 1000L;
                    exoPlayer.seekTo(posMs);
                }
                startProgressUpdater();
            }
        });

        startProgressUpdater();

        // Volume management
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager != null) {
            int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            volumeSeekBar.setMax(max);
            volumeSeekBar.setProgress(cur);
            updateVolumeText(cur, max);

            volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
                        updateVolumeText(progress, seekBar.getMax());
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        // Gesture controls
        setupGestureControls();
    }

    private void setupGestureControls() {
        final GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (e1 == null || e2 == null) return false;
                float deltaX = e2.getX() - e1.getX();
                float deltaY = e2.getY() - e1.getY();

                if (Math.abs(deltaX) > Math.abs(deltaY)) {
                    float screenWidth = playerView.getWidth();
                    float seekPercentage = deltaX / screenWidth;
                    long duration = exoPlayer.getDuration();

                    if (duration > 0) {
                        long seekAmount = (long) (seekPercentage * duration * 0.1);
                        long newPosition = exoPlayer.getCurrentPosition() + seekAmount;
                        newPosition = Math.max(0, Math.min(newPosition, duration));
                        exoPlayer.seekTo(newPosition);
                    }
                    return true;
                }

                if (Math.abs(deltaY) > Math.abs(deltaX)) {
                    float screenWidth = playerView.getWidth();
                    if (e1.getX() > screenWidth / 2) {
                        adjustVolume(distanceY * -1);
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                toggleControls();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (exoPlayer.isPlaying()) exoPlayer.pause();
                else exoPlayer.play();
                updatePlayPauseIcon();
                return true;
            }
        });

        final ScaleGestureDetector scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            float scaleFactor = 1.0f;
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                scaleFactor *= detector.getScaleFactor();
                scaleFactor = Math.max(1.0f, Math.min(scaleFactor, 3.0f));
                playerView.setScaleX(scaleFactor);
                playerView.setScaleY(scaleFactor);
                return true;
            }
        });

        playerView.setOnTouchListener((v, event) -> {
            scaleGestureDetector.onTouchEvent(event);
            boolean handledByGesture = gestureDetector.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_UP && handledByGesture) v.performClick();
            return true;
        });
    }

    private void adjustVolume(float delta) {
        if (audioManager == null) return;
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        float screenHeight = playerView.getHeight();
        int volumeChange = (int) ((delta / screenHeight) * maxVolume);
        int newVolume = currentVolume + volumeChange;
        newVolume = Math.max(0, Math.min(newVolume, maxVolume));
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0);
        volumeSeekBar.setProgress(newVolume);
        updateVolumeText(newVolume, maxVolume);
    }

    private void updatePlayPauseIcon() {
        if (exoPlayer.isPlaying()) btnPlayPause.setImageResource(R.drawable.ic_pause);
        else btnPlayPause.setImageResource(R.drawable.ic_play);
    }

    private void toggleControls() {
        controlsVisible = !controlsVisible;
        controlLayout.setVisibility(controlsVisible ? View.VISIBLE : View.GONE);
        topRightControls.setVisibility(controlsVisible ? View.VISIBLE : View.GONE);
        txtVideoTitle.setVisibility(controlsVisible ? View.VISIBLE : View.GONE);
        if (!controlsVisible) {
            volumeVisible = false;
            volumeLayout.setVisibility(View.GONE);
        }
    }

    private void updateVolumeText(int current, int max) {
        int percentage = (int) ((current / (float) max) * 100);
        txtVolume.setText(percentage + "%");
    }

    private void setupPlaylist() {
        exoPlayer.clearMediaItems();

        for (String path : videoList) {
            // Removed streamingServer.registerVideo(path) call
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
                builder.setSubtitleConfigurations(java.util.Collections.singletonList(subtitleConfig));
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

    private void showAudioTrackDialog() {
        if (exoPlayer == null) return;

        Tracks currentTracks = exoPlayer.getCurrentTracks();
        List<String> audioTrackNames = new ArrayList<>();
        List<Tracks.Group> audioTrackGroups = new ArrayList<>();
        List<Integer> audioTrackIndices = new ArrayList<>();
        int currentSelectedIndex = -1;

        int trackCounter = 0;
        for (Tracks.Group trackGroup : currentTracks.getGroups()) {
            if (trackGroup.getType() == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                for (int i = 0; i < trackGroup.length; i++) {
                    androidx.media3.common.Format format = trackGroup.getTrackFormat(i);

                    String trackName = "Audio Track " + (trackCounter + 1);
                    if (format.language != null && !format.language.isEmpty()) {
                        trackName = format.language.toUpperCase();
                        if (format.label != null && !format.label.isEmpty()) {
                            trackName += " - " + format.label;
                        }
                    } else if (format.label != null && !format.label.isEmpty()) {
                        trackName = format.label;
                    }

                    if (format.sampleMimeType != null) {
                        String codec = format.sampleMimeType.replace("audio/", "").toUpperCase();
                        trackName += " (" + codec + ")";
                    }

                    audioTrackNames.add(trackName);
                    audioTrackGroups.add(trackGroup);
                    audioTrackIndices.add(i);

                    if (trackGroup.isTrackSelected(i)) currentSelectedIndex = trackCounter;

                    trackCounter++;
                }
            }
        }

        if (audioTrackNames.isEmpty()) {
            Toast.makeText(this, "No audio tracks available", Toast.LENGTH_SHORT).show();
            return;
        }

        if (audioTrackNames.size() == 1) {
            Toast.makeText(this, "Only one audio track available", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Audio Track");

        builder.setSingleChoiceItems(
                audioTrackNames.toArray(new String[0]),
                currentSelectedIndex,
                (dialog, which) -> {
                    setAudioTrack(audioTrackGroups.get(which), audioTrackIndices.get(which));
                    dialog.dismiss();
                    Toast.makeText(this, "Audio: " + audioTrackNames.get(which), Toast.LENGTH_SHORT).show();
                }
        );

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void setAudioTrack(Tracks.Group trackGroup, int trackIndex) {
        if (exoPlayer == null) return;

        try {
            androidx.media3.common.TrackSelectionParameters.Builder parametersBuilder =
                    exoPlayer.getTrackSelectionParameters().buildUpon();

            parametersBuilder.clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_AUDIO);

            androidx.media3.common.TrackSelectionOverride override =
                    new androidx.media3.common.TrackSelectionOverride(
                            trackGroup.getMediaTrackGroup(),
                            java.util.Collections.singletonList(trackIndex)
                    );

            parametersBuilder.setOverrideForType(override);

            exoPlayer.setTrackSelectionParameters(parametersBuilder.build());

        } catch (Exception e) {
            Toast.makeText(this, "Failed to set audio track: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e(TAG, "setAudioTrack failed", e);
        }
    }
}