package com.bhuvan.sunobybhuvan;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import java.util.ArrayList;

public class PlaybackService extends Service {

    public static final String ACTION_PLAY = "com.bhuvan.sunobybhuvan.PLAY";
    public static final String ACTION_PAUSE = "com.bhuvan.sunobybhuvan.PAUSE";
    public static final String ACTION_NEXT = "com.bhuvan.sunobybhuvan.NEXT";
    public static final String ACTION_PREVIOUS = "com.bhuvan.sunobybhuvan.PREVIOUS";
    public static final String ACTION_STOP = "com.bhuvan.sunobybhuvan.STOP";
    public static final String ACTION_SEEK = "com.bhuvan.sunobybhuvan.SEEK";
    public static final String ACTION_REFRESH = "com.bhuvan.sunobybhuvan.REFRESH";
    public static final String ACTION_PROGRESS = "com.bhuvan.sunobybhuvan.PROGRESS";
    public static final String ACTION_ERROR = "com.bhuvan.sunobybhuvan.ERROR";

    public static final String EXTRA_INDEX = "index";
    public static final String EXTRA_POSITION = "position";
    public static final String EXTRA_DURATION = "duration";
    public static final String EXTRA_PLAYING = "playing";
    public static final String EXTRA_PREPARED = "prepared";
    public static final String EXTRA_SEEK_POSITION = "seek_position";
    public static final String EXTRA_MESSAGE = "message";

    public static class Song {
        public final Uri uri;
        public final String key;
        public String title;
        public String artist;
        public long durationMs;
        public volatile boolean metadataLoaded;

        public Song(Uri uri, String key, String title) {
            this.uri = uri;
            this.key = key;
            this.title = title;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Song && ((Song) other).key.equals(key);
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }
    }

    // The music library is shared with MainActivity (same process, main thread only).
    public static final ArrayList<Song> library = new ArrayList<>();
    public static Song currentSong = null;

    private static final String CHANNEL_ID = "suno_playback";
    private static final int NOTIFICATION_ID = 1001;

    private MediaPlayer player;
    private boolean prepared = false;
    private boolean isForeground = false;
    private boolean pausedByFocusLoss = false;
    private boolean noisyRegistered = false;
    private int errorStreak = 0;

    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            sendProgress();
            if (isPlayerPlaying()) {
                handler.postDelayed(this, 500);
            }
        }
    };

    private final AudioManager.OnAudioFocusChangeListener focusListener =
            this::onAudioFocusChange;

    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && AudioManager.ACTION_AUDIO_BECOMING_NOISY
                    .equals(intent.getAction())) {
                pausedByFocusLoss = false;
                pausePlayback();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        createNotificationChannel();

        IntentFilter filter =
                new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(noisyReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(noisyReceiver, filter);
        }
        noisyRegistered = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();

        if (action != null) {
            switch (action) {
                case ACTION_PLAY:
                    handlePlay(intent);
                    break;
                case ACTION_PAUSE:
                    pausedByFocusLoss = false;
                    pausePlayback();
                    break;
                case ACTION_NEXT:
                    playRelative(1);
                    break;
                case ACTION_PREVIOUS:
                    playRelative(-1);
                    break;
                case ACTION_SEEK:
                    seekToPosition(intent.getIntExtra(EXTRA_SEEK_POSITION, 0));
                    break;
                case ACTION_REFRESH:
                    if (player == null) {
                        currentSong = null;
                    }
                    sendProgress();
                    break;
                case ACTION_STOP:
                    stopEverything();
                    break;
                default:
                    break;
            }
        }

        if (player == null && !isForeground) {
            currentSong = null;
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private void handlePlay(Intent intent) {
        // Always become a foreground service first (required after startForegroundService).
        ensureForeground();

        int index = intent.getIntExtra(EXTRA_INDEX, -1);
        errorStreak = 0;

        if (index >= 0) {
            if (index >= library.size()) {
                reportError("That song is no longer in your library.");
                stopEverything();
                return;
            }

            Song song = library.get(index);

            if (player != null && prepared && song.equals(currentSong)) {
                resumePlayback();
            } else {
                startSong(song);
            }
        } else if (player != null && prepared) {
            resumePlayback();
        } else if (currentSong != null) {
            startSong(currentSong);
        } else if (!library.isEmpty()) {
            startSong(library.get(0));
        } else {
            stopEverything();
        }
    }

    private void startSong(Song song) {
        releasePlayer();
        currentSong = song;
        pausedByFocusLoss = false;

        if (!requestAudioFocus()) {
            reportError("Another app is using the audio right now. Please try again.");
            updateNotification();
            sendProgress();
            return;
        }

        try {
            MediaPlayer mp = new MediaPlayer();
            player = mp;

            mp.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build());
            mp.setWakeMode(getApplicationContext(),
                    PowerManager.PARTIAL_WAKE_LOCK);
            mp.setDataSource(this, song.uri);
            mp.setOnPreparedListener(this::onPlayerPrepared);
            mp.setOnCompletionListener(this::onPlayerCompleted);
            mp.setOnErrorListener(this::onPlayerError);
            mp.prepareAsync();
        } catch (Exception e) {
            onSongFailed(song);
            return;
        }

        updateNotification();
        sendProgress();
    }

    private void onPlayerPrepared(MediaPlayer mp) {
        if (mp != player) return;

        prepared = true;
        errorStreak = 0;

        try {
            if (currentSong != null && currentSong.durationMs <= 0) {
                currentSong.durationMs = mp.getDuration();
            }
            mp.start();
        } catch (IllegalStateException e) {
            onSongFailed(currentSong);
            return;
        }

        updateNotification();
        startProgressUpdates();
        sendProgress();
    }

    private void onPlayerCompleted(MediaPlayer mp) {
        if (mp != player) return;

        handler.removeCallbacks(progressRunnable);

        if (library.size() > 1) {
            playRelative(1);
        } else {
            sendProgress();
            updateNotification();
        }
    }

    private boolean onPlayerError(MediaPlayer mp, int what, int extra) {
        if (mp == player) {
            onSongFailed(currentSong);
        }
        return true;
    }

    private void onSongFailed(Song song) {
        releasePlayer();
        errorStreak++;

        if (errorStreak == 1) {
            String name = song == null ? "this song" : song.title;
            reportError("Can't play \"" + name
                    + "\". The file may be missing or in an unsupported format.");
        }

        if (library.size() > 1 && errorStreak < library.size()) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    playRelative(1);
                }
            });
        } else {
            if (errorStreak > 1) {
                reportError("None of the songs could be played.");
            }
            updateNotification();
            sendProgress();
        }
    }

    private void pausePlayback() {
        if (player != null && prepared) {
            try {
                if (player.isPlaying()) {
                    player.pause();
                }
            } catch (IllegalStateException ignored) {
            }
        }

        handler.removeCallbacks(progressRunnable);
        updateNotification();
        sendProgress();
    }

    private void resumePlayback() {
        if (player == null || !prepared) return;

        if (!requestAudioFocus()) {
            reportError("Another app is using the audio right now. Please try again.");
            return;
        }

        try {
            player.setVolume(1f, 1f);
            player.start();
        } catch (IllegalStateException e) {
            onSongFailed(currentSong);
            return;
        }

        updateNotification();
        startProgressUpdates();
        sendProgress();
    }

    private void playRelative(int delta) {
        if (library.isEmpty()) {
            stopEverything();
            return;
        }

        if (delta < 0 && player != null && prepared) {
            try {
                if (player.getCurrentPosition() > 3000) {
                    seekToPosition(0);
                    return;
                }
            } catch (IllegalStateException ignored) {
            }
        }

        if (!isForeground) {
            ensureForeground();
        }

        int size = library.size();
        int index = currentSong == null ? -1 : library.indexOf(currentSong);
        int target;

        if (index < 0) {
            target = delta > 0 ? 0 : size - 1;
        } else {
            target = ((index + delta) % size + size) % size;
        }

        startSong(library.get(target));
    }

    private void seekToPosition(int position) {
        if (player == null || !prepared) return;

        try {
            int duration = player.getDuration();
            position = Math.max(0, Math.min(position, duration));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                player.seekTo(position, MediaPlayer.SEEK_CLOSEST);
            } else {
                player.seekTo(position);
            }

            sendProgress();
        } catch (IllegalStateException ignored) {
        }
    }

    private void stopEverything() {
        handler.removeCallbacks(progressRunnable);
        releasePlayer();
        abandonAudioFocus();
        currentSong = null;
        sendProgress();

        if (isForeground) {
            stopForeground(true);
            isForeground = false;
        }

        stopSelf();
    }

    private boolean isPlayerPlaying() {
        try {
            return player != null && prepared && player.isPlaying();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private void startProgressUpdates() {
        handler.removeCallbacks(progressRunnable);
        handler.post(progressRunnable);
    }

    private void sendProgress() {
        int position = 0;
        int duration = 0;
        boolean playing = false;

        if (player != null && prepared) {
            try {
                position = player.getCurrentPosition();
                duration = player.getDuration();
                playing = player.isPlaying();
            } catch (IllegalStateException ignored) {
            }
        }

        Intent intent = new Intent(ACTION_PROGRESS);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_POSITION, position);
        intent.putExtra(EXTRA_DURATION, duration);
        intent.putExtra(EXTRA_PLAYING, playing);
        intent.putExtra(EXTRA_PREPARED, prepared);
        intent.putExtra(EXTRA_INDEX,
                currentSong == null ? -1 : library.indexOf(currentSong));
        sendBroadcast(intent);
    }

    private void reportError(String message) {
        Intent intent = new Intent(ACTION_ERROR);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_MESSAGE, message);
        sendBroadcast(intent);
    }

    private void onAudioFocusChange(int change) {
        switch (change) {
            case AudioManager.AUDIOFOCUS_LOSS:
                pausedByFocusLoss = false;
                pausePlayback();
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (isPlayerPlaying()) {
                    pausePlayback();
                    pausedByFocusLoss = true;
                }
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                if (player != null) {
                    try {
                        player.setVolume(0.2f, 0.2f);
                    } catch (IllegalStateException ignored) {
                    }
                }
                break;

            case AudioManager.AUDIOFOCUS_GAIN:
                if (player != null) {
                    try {
                        player.setVolume(1f, 1f);
                    } catch (IllegalStateException ignored) {
                    }
                }
                if (pausedByFocusLoss) {
                    pausedByFocusLoss = false;
                    resumePlayback();
                }
                break;

            default:
                break;
        }
    }

    private boolean requestAudioFocus() {
        if (audioManager == null) return true;

        int result;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (focusRequest == null) {
                focusRequest = new AudioFocusRequest.Builder(
                        AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(
                                new AudioAttributes.Builder()
                                        .setUsage(AudioAttributes.USAGE_MEDIA)
                                        .setContentType(
                                                AudioAttributes.CONTENT_TYPE_MUSIC)
                                        .build())
                        .setOnAudioFocusChangeListener(focusListener)
                        .build();
            }
            result = audioManager.requestAudioFocus(focusRequest);
        } else {
            result = audioManager.requestAudioFocus(
                    focusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);
        }

        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void abandonAudioFocus() {
        if (audioManager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (focusRequest != null) {
                audioManager.abandonAudioFocusRequest(focusRequest);
            }
        } else {
            audioManager.abandonAudioFocus(focusListener);
        }
    }

    private void ensureForeground() {
        try {
            Notification notification = buildNotification();

            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }

            isForeground = true;
        } catch (RuntimeException e) {
            isForeground = false;
        }
    }

    private PendingIntent serviceIntent(String action, int requestCode) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(action);

        return PendingIntent.getService(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification buildNotification() {
        boolean playing = isPlayerPlaying();

        String title = currentSong != null
                ? currentSong.title : "Suno by Bhuvan";

        String text;
        if (currentSong != null && currentSong.artist != null
                && !currentSong.artist.trim().isEmpty()) {
            text = currentSong.artist;
        } else {
            text = playing ? "Playing" : "Suno by Bhuvan";
        }

        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                0,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .addAction(android.R.drawable.ic_media_previous,
                        "Previous",
                        serviceIntent(ACTION_PREVIOUS, 10))
                .addAction(
                        playing
                                ? android.R.drawable.ic_media_pause
                                : android.R.drawable.ic_media_play,
                        playing ? "Pause" : "Play",
                        serviceIntent(playing ? ACTION_PAUSE : ACTION_PLAY, 11))
                .addAction(android.R.drawable.ic_media_next,
                        "Next",
                        serviceIntent(ACTION_NEXT, 12))
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                        "Stop",
                        serviceIntent(ACTION_STOP, 13))
                .setStyle(new Notification.MediaStyle()
              
