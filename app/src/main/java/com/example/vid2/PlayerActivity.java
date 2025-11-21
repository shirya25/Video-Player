package com.example.vid2;

import android.app.AlertDialog;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.mediarouter.app.MediaRouteButton;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;
import androidx.media3.cast.CastPlayer;
import androidx.media3.cast.SessionAvailabilityListener;

import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManagerListener;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@UnstableApi
public class PlayerActivity extends AppCompatActivity {

    private PlayerView playerView;
    private ExoPlayer exoPlayer;
    private DefaultTrackSelector trackSelector;
    private CastPlayer castPlayer;
    private Player currentPlayer;
    private CastContext castContext;

    private ImageButton btnPlayPause, btnForward, btnRewind, btnNext, btnPrev,
            btnSpeed, btnSubtitle, btnLoadSubtitle, btnVolume, btnAspectRatio, btnAudioTrack;
    private SeekBar seekBar, volumeSeekBar;
    private TextView txtCurrentTime, txtTotalTime, txtSpeed, txtVolume, txtVideoTitle;
    private SubtitleView subtitleView;
    private View volumeLayout, controlLayout, topRightControls;
    private MediaRouteButton castButton;

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
    private boolean isCasting = false;

    // Gesture controls
    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleGestureDetector;
    private float scaleFactor = 1.0f;
    private static final float MIN_ZOOM = 1.0f;
    private static final float MAX_ZOOM = 3.0f;

    private final SessionManagerListener<CastSession> castSessionManagerListener =
            new SessionManagerListener<CastSession>() {
                @Override
                public void onSessionStarted(CastSession session, String sessionId) {
                    onCastSessionStarted();
                }

                @Override
                public void onSessionEnded(CastSession session, int error) {
                    onCastSessionEnded();
                }

                @Override
                public void onSessionResumed(CastSession session, boolean wasSuspended) {
                    onCastSessionStarted();
                }

                @Override
                public void onSessionSuspended(CastSession session, int reason) {
                    onCastSessionEnded();
                }

                @Override
                public void onSessionStarting(CastSession session) {}

                @Override
                public void onSessionEnding(CastSession session) {}

                @Override
                public void onSessionResuming(CastSession session, String sessionId) {}

                @Override
                public void onSessionStartFailed(CastSession session, int error) {
                    Toast.makeText(PlayerActivity.this, "Cast failed to start", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onSessionResumeFailed(CastSession session, int error) {}
            };

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

        // Get video list
        ArrayList<String> passed = getIntent().getStringArrayListExtra("videoList");
        if (passed != null) videoList = passed;
        currentIndex = getIntent().getIntExtra("currentIndex", 0);
        if (videoList.isEmpty()) {
            Toast.makeText(this, "No videos provided", Toast.LENGTH_LONG).show();
        }

        // Initialize Cast
        try {
            castContext = CastContext.getSharedInstance(this);
            castPlayer = new CastPlayer(castContext);
            castPlayer.setSessionAvailabilityListener(new SessionAvailabilityListener() {
                @Override
                public void onCastSessionAvailable() {
                    onCastSessionStarted();
                }

                @Override
                public void onCastSessionUnavailable() {
                    onCastSessionEnded();
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Cast not available", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }

        // Initialize player with better codec support
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
        currentPlayer = exoPlayer;

        // Register cast session listener
        if (castContext != null) {
            castContext.getSessionManager().addSessionManagerListener(
                    castSessionManagerListener, CastSession.class);
        }

        // Set video title
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

        // Setup initial state
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

        // Aspect Ratio toggle
        btnAspectRatio.setOnClickListener(v -> {
            isFullScreenMode = !isFullScreenMode;
            if (isFullScreenMode) {
                // Fill screen - zoom to fill
                playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
                btnAspectRatio.setImageResource(R.drawable.ic_fullscreen_exit);
                Toast.makeText(this, "Fill Screen", Toast.LENGTH_SHORT).show();
            } else {
                // Fit screen - show entire video
                playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
                btnAspectRatio.setImageResource(R.drawable.ic_fullscreen);
                Toast.makeText(this, "Fit Screen", Toast.LENGTH_SHORT).show();
            }
        });

        // Audio Track Selection
        btnAudioTrack.setOnClickListener(v -> showAudioTrackDialog());

        // SeekBar for video progress
        seekBar.setMax(1000); // Set max to 1000 for better precision
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser && exoPlayer.getDuration() > 0) {
                    long posMs = (long) progress * exoPlayer.getDuration() / 1000L;
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
                    long posMs = (long) bar.getProgress() * exoPlayer.getDuration() / 1000L;
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


        // Setup gesture controls
        setupGestureControls();
    }

    private void setupGestureControls() {
        // Gesture detector for swipe and single/double tap
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (e1 == null || e2 == null) return false;

                float deltaX = e2.getX() - e1.getX();
                float deltaY = e2.getY() - e1.getY();

                // Check if horizontal swipe (seeking)
                if (Math.abs(deltaX) > Math.abs(deltaY)) {
                    // Horizontal swipe - seek video
                    float screenWidth = playerView.getWidth();
                    float seekPercentage = deltaX / screenWidth;
                    long duration = exoPlayer.getDuration();

                    if (duration > 0) {
                        long seekAmount = (long) (seekPercentage * duration * 0.1); // 10% per full swipe
                        long newPosition = exoPlayer.getCurrentPosition() + seekAmount;
                        newPosition = Math.max(0, Math.min(newPosition, duration));
                        exoPlayer.seekTo(newPosition);

                        // Show seek feedback
                        String feedback = seekAmount > 0 ? "+%ds" : "%ds";
                        Toast.makeText(PlayerActivity.this,
                                String.format(feedback, seekAmount / 1000),
                                Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }

                // Vertical swipe on right side - volume
                if (Math.abs(deltaY) > Math.abs(deltaX)) {
                    float screenWidth = playerView.getWidth();
                    if (e1.getX() > screenWidth / 2) {
                        // Right side - volume control
                        adjustVolume(distanceY * -1);
                        return true;
                    }
                }

                return false;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                // SINGLE TAP: Toggle controls visibility
                toggleControls();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                // DOUBLE TAP: Toggle play/pause
                if (exoPlayer.isPlaying()) {
                    exoPlayer.pause();
                } else {
                    exoPlayer.play();
                }
                updatePlayPauseIcon();
                return true;
            }
        });

        // Scale gesture detector for pinch to zoom
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                scaleFactor *= detector.getScaleFactor();
                scaleFactor = Math.max(MIN_ZOOM, Math.min(scaleFactor, MAX_ZOOM));

                // Apply zoom
                playerView.setScaleX(scaleFactor);
                playerView.setScaleY(scaleFactor);

                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                // Show current zoom level
                Toast.makeText(PlayerActivity.this,
                        String.format("Zoom: %.1fx", scaleFactor),
                        Toast.LENGTH_SHORT).show();
            }
        });

        // Override touch events on playerView
        playerView.setOnTouchListener((v, event) -> {
            // 1. Handle scale gestures first (pinch to zoom)
            scaleGestureDetector.onTouchEvent(event);

            // 2. Then handle other gestures (swipe, tap)
            boolean handledByGesture = gestureDetector.onTouchEvent(event);

            // 3. Crucial for Accessibility/Lint: Call performClick on ACTION_UP
            // We call performClick() to ensure accessibility services (like TalkBack) are aware of the click action.
            if (event.getAction() == MotionEvent.ACTION_UP && handledByGesture) {
                v.performClick();
            }

            // Return true to consume the event for the gesture detectors
            return true;
        });
    }

    private void adjustVolume(float delta) {
        if (audioManager == null) return;

        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

        // Convert delta to volume change (swipe full height = max volume change)
        float screenHeight = playerView.getHeight();
        int volumeChange = (int) ((delta / screenHeight) * maxVolume);

        int newVolume = currentVolume + volumeChange;
        newVolume = Math.max(0, Math.min(newVolume, maxVolume));

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0);
        volumeSeekBar.setProgress(newVolume);
        updateVolumeText(newVolume, maxVolume);

        // Show volume feedback
        int percentage = (int) ((newVolume / (float) maxVolume) * 100);
        Toast.makeText(this, "Volume: " + percentage + "%", Toast.LENGTH_SHORT).show();
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
        topRightControls.setVisibility(controlsVisible ? View.VISIBLE : View.GONE);
        txtVideoTitle.setVisibility(controlsVisible ? View.VISIBLE : View.GONE);

        // Hide volume bar when controls are hidden
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

        // Unregister cast listener
        if (castContext != null) {
            castContext.getSessionManager().removeSessionManagerListener(
                    castSessionManagerListener, CastSession.class);
        }

        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }

        if (castPlayer != null) {
            castPlayer.setSessionAvailabilityListener(null);
            castPlayer.release();
            castPlayer = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.cast_menu, menu);
        MenuItem mediaRouteMenuItem = menu.findItem(R.id.media_route_menu_item);
        if (mediaRouteMenuItem != null) {
            CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), menu, R.id.media_route_menu_item);
        }
        return true;
    }

    private void onCastSessionStarted() {
        isCasting = true;
        Toast.makeText(this, "Casting started", Toast.LENGTH_SHORT).show();

        // Save current playback position
        long currentPosition = exoPlayer.getCurrentPosition();
        boolean wasPlaying = exoPlayer.isPlaying();

        // Switch to cast player
        exoPlayer.pause();
        currentPlayer = castPlayer;

        // Transfer media to cast player
        setupPlaylistForCast();
        castPlayer.seekTo(currentIndex, currentPosition);
        castPlayer.prepare();
        if (wasPlaying) {
            castPlayer.play();
        }

        // Hide local video view
        playerView.setVisibility(View.GONE);
        txtVideoTitle.setText(txtVideoTitle.getText() + " (Casting)");
    }

    private void onCastSessionEnded() {
        if (!isCasting) return;

        isCasting = false;
        Toast.makeText(this, "Casting ended", Toast.LENGTH_SHORT).show();

        // Save cast playback position
        long currentPosition = castPlayer.getCurrentPosition();
        boolean wasPlaying = castPlayer.isPlaying();

        // Switch back to local player
        castPlayer.pause();
        currentPlayer = exoPlayer;

        // Resume local playback
        exoPlayer.seekTo(currentIndex, currentPosition);
        if (wasPlaying) {
            exoPlayer.play();
        }

        // Show local video view
        playerView.setVisibility(View.VISIBLE);

        // Update title
        if (currentIndex < videoList.size()) {
            File videoFile = new File(videoList.get(currentIndex));
            txtVideoTitle.setText(videoFile.getName());
        }
    }

    private void setupPlaylistForCast() {
        castPlayer.clearMediaItems();
        for (String path : videoList) {
            MediaItem mediaItem = buildMediaItemForCast(path);
            castPlayer.addMediaItem(mediaItem);
        }
    }

    private MediaItem buildMediaItemForCast(String videoPath) {
        File videoFile = new File(videoPath);

        // For casting, we need to provide a URL that the cast device can access
        // Local files won't work directly - you'd need to set up a local server
        // For now, we'll use the file URI (this works for some scenarios)

        MediaItem.Builder builder = new MediaItem.Builder()
                .setUri(Uri.fromFile(videoFile))
                .setMediaMetadata(
                        new androidx.media3.common.MediaMetadata.Builder()
                                .setTitle(videoFile.getName())
                                .build()
                );

        return builder.build();
    }

    private void showAudioTrackDialog() {
        if (exoPlayer == null) return;

        Tracks currentTracks = exoPlayer.getCurrentTracks();
        List<String> audioTrackNames = new ArrayList<>();
        List<Tracks.Group> audioTrackGroups = new ArrayList<>();
        List<Integer> audioTrackIndices = new ArrayList<>();
        int currentSelectedIndex = -1;

        // Get all audio tracks
        int trackCounter = 0;
        for (Tracks.Group trackGroup : currentTracks.getGroups()) {
            if (trackGroup.getType() == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                for (int i = 0; i < trackGroup.length; i++) {
                    androidx.media3.common.Format format = trackGroup.getTrackFormat(i);

                    // Build track name
                    String trackName = "Audio Track " + (trackCounter + 1);
                    if (format.language != null && !format.language.isEmpty()) {
                        trackName = format.language.toUpperCase();
                        // Add label if available
                        if (format.label != null && !format.label.isEmpty()) {
                            trackName += " - " + format.label;
                        }
                    } else if (format.label != null && !format.label.isEmpty()) {
                        trackName = format.label;
                    }

                    // Add codec info
                    if (format.sampleMimeType != null) {
                        String codec = format.sampleMimeType.replace("audio/", "").toUpperCase();
                        trackName += " (" + codec + ")";
                    }

                    audioTrackNames.add(trackName);
                    audioTrackGroups.add(trackGroup);
                    audioTrackIndices.add(i);

                    // Check if this track is currently selected
                    if (trackGroup.isTrackSelected(i)) {
                        currentSelectedIndex = trackCounter;
                    }

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

        // Create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Audio Track");

        builder.setSingleChoiceItems(
                audioTrackNames.toArray(new String[0]),
                currentSelectedIndex,
                (dialog, which) -> {
                    // Set the selected audio track
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
            // 1. Get the current TrackSelectionParameters and start building.
            androidx.media3.common.TrackSelectionParameters.Builder parametersBuilder =
                    exoPlayer.getTrackSelectionParameters().buildUpon();

            // 2. Clear any existing selection/override for the AUDIO track type.
            // This is the key change to avoid the error. C.TRACK_TYPE_AUDIO is an int.
            parametersBuilder.clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_AUDIO);

            // 3. Create the TrackSelectionOverride for the specific track in the group.
            androidx.media3.common.TrackSelectionOverride override =
                    new androidx.media3.common.TrackSelectionOverride(
                            trackGroup.getMediaTrackGroup(),
                            java.util.Collections.singletonList(trackIndex)
                    );

            // 4. Apply the override to the parameters.
            // setOverrideForType is the standard way to apply a track selection in Media3.
            parametersBuilder.setOverrideForType(override);

            // 5. Apply the new parameters to the player.
            exoPlayer.setTrackSelectionParameters(parametersBuilder.build());

        } catch (Exception e) {
            Toast.makeText(this, "Failed to set audio track: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
}