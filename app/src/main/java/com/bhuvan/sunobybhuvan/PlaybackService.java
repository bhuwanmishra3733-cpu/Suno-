package com.bhuvan.sunobybhuvan;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.io.IOException;

public class PlaybackService extends Service {

    public static final String ACTION_PLAY =
            "com.bhuvan.sunobybhuvan.PLAY";

    public static final String ACTION_PAUSE =
            "com.bhuvan.sunobybhuvan.PAUSE";

    public static final String ACTION_STOP =
            "com.bhuvan.sunobybhuvan.STOP";

    public static final String ACTION_SEEK =
            "com.bhuvan.sunobybhuvan.SEEK";

    public static final String ACTION_PROGRESS =
            "com.bhuvan.sunobybhuvan.PROGRESS";

    public static final String EXTRA_AUDIO_URI = "audio_uri";
    public static final String EXTRA_SONG_NAME = "song_name";
    public static final String EXTRA_POSITION = "position";
    public static final String EXTRA_DURATION = "duration";
    public static final String EXTRA_PLAYING = "playing";
    public static final String EXTRA_SEEK_POSITION = "seek_position";
    public static final String EXTRA_PREPARED = "prepared";

    private static final String CHANNEL_ID = "suno_playback";
    private static final int NOTIFICATION_ID = 1001;

    private MediaPlayer player;

    private String songName = "Suno by Bhuvan";

    private boolean prepared = false;
    private boolean isForeground = false;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {

            sendProgress();

            if (prepared && player != null) {
                handler.postDelayed(this, 500);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId) {

        if (intent == null) {
            return START_NOT_STICKY;
        }

        String action = intent.getAction();

        if (ACTION_PLAY.equals(action)) {

            String uriString =
                    intent.getStringExtra(EXTRA_AUDIO_URI);

            String receivedName =
                    intent.getStringExtra(EXTRA_SONG_NAME);

            if (receivedName != null
                    && !receivedName.isEmpty()) {
                songName = receivedName;
            }

            if (uriString != null) {

                if (Build.VERSION.SDK_INT
                        >= Build.VERSION_CODES.O) {

                    startForeground(
                            NOTIFICATION_ID,
                            buildNotification(false)
                    );

                } else {

                    startForeground(
                            NOTIFICATION_ID,
                            buildNotification(false)
                    );
                }

                isForeground = true;

                prepareAndPlay(Uri.parse(uriString));

            } else if (prepared && player != null) {

                try {
                    player.start();
                    updateNotification(true);
                    startProgressUpdates();
                    sendProgress();

                } catch (IllegalStateException ignored) {
                }
            }

        } else if (ACTION_PAUSE.equals(action)) {

            if (player != null
                    && prepared
                    && player.isPlaying()) {

                player.pause();
            }

            updateNotification(false);
            sendProgress();

        } else if (ACTION_SEEK.equals(action)) {

            int position = intent.getIntExtra(
                    EXTRA_SEEK_POSITION,
                    0
            );

            seekToPosition(position);

        } else if (ACTION_STOP.equals(action)) {

            stopPlayback();

            if (isForeground) {
                stopForeground(true);
                isForeground = false;
            }

            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private void prepareAndPlay(Uri uri) {

        releasePlayer();

        try {

            player = new MediaPlayer();

            player.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setUsage(
                                    AudioAttributes.USAGE_MEDIA
                            )
                            .setContentType(
                                    AudioAttributes.CONTENT_TYPE_MUSIC
                            )
                            .build()
            );

            player.setDataSource(this, uri);

            player.setOnPreparedListener(mp -> {

                prepared = true;

                try {
                    mp.start();
                } catch (IllegalStateException e) {
                    sendProgress();
                    return;
                }

                updateNotification(true);
                startProgressUpdates();
                sendProgress();
            });

            player.setOnCompletionListener(mp -> {

                handler.removeCallbacks(progressRunnable);

                sendProgress();
                updateNotification(false);
            });

            player.setOnErrorListener((mp, what, extra) -> {

                releasePlayer();
                sendProgress();

                if (isForeground) {
                    stopForeground(true);
                    isForeground = false;
                }

                stopSelf();

                return true;
            });

            player.prepareAsync();

        } catch (IOException | IllegalStateException e) {

            releasePlayer();
            sendProgress();

            if (isForeground) {
                stopForeground(true);
                isForeground = false;
            }

            stopSelf();
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

        Intent progressIntent = new Intent(ACTION_PROGRESS);

        progressIntent.setPackage(getPackageName());

        progressIntent.putExtra(
                EXTRA_POSITION,
                position
        );

        progressIntent.putExtra(
                EXTRA_DURATION,
                duration
        );

        progressIntent.putExtra(
                EXTRA_PLAYING,
                playing
        );

        progressIntent.putExtra(
                EXTRA_PREPARED,
                prepared
        );

        sendBroadcast(progressIntent);
    }

    private void seekToPosition(int position) {

        if (player == null || !prepared) {
            return;
        }

        try {

            int duration = player.getDuration();

            position = Math.max(
                    0,
                    Math.min(position, duration)
            );

            if (Build.VERSION.SDK_INT
                    >= Build.VERSION_CODES.O) {

                player.seekTo(
                        position,
                        MediaPlayer.SEEK_CLOSEST
                );

            } else {

                player.seekTo(position);
            }

            sendProgress();

        } catch (IllegalStateException ignored) {
        }
    }

    private Notification buildNotification(boolean playing) {

        Intent openApp = new Intent(
                this,
                MainActivity.class
        );

        PendingIntent contentIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        openApp,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE
                );

        Intent controlIntent =
                new Intent(this, PlaybackService.class);

        controlIntent.setAction(
                playing ? ACTION_PAUSE : ACTION_PLAY
        );

        PendingIntent controlPendingIntent =
                PendingIntent.getService(
                        this,
                        1,
                        controlIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE
                );

        Notification.Builder builder;

        if (Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.O) {

            builder = new Notification.Builder(
                    this,
                    CHANNEL_ID
            );

        } else {

            builder = new Notification.Builder(this);
        }

        builder.setContentTitle(songName)
                .setContentText(
                        playing
                                ? "Playing • Suno by Bhuvan"
                                : "Paused • Suno by Bhuvan"
                )
                .setSmallIcon(
                        android.R.drawable.ic_media_play
                )
                .setContentIntent(contentIntent)
                .setOngoing(playing)
                .addAction(
                        playing
                                ? android.R.drawable.ic_media_pause
                                : android.R.drawable.ic_media_play,
                        playing ? "Pause" : "Play",
                        controlPendingIntent
                );

        return builder.build();
    }

    private void updateNotification(boolean playing) {

        NotificationManager manager =
                (NotificationManager) getSystemService(
                        NOTIFICATION_SERVICE
                );

        if (manager != null && isForeground) {

            manager.notify(
                    NOTIFICATION_ID,
                    buildNotification(playing)
            );
        }
    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "Suno Music Playback",
                            NotificationManager.IMPORTANCE_LOW
                    );

            channel.setDescription(
                    "Music playback controls"
            );

            NotificationManager manager =
                    getSystemService(
                            NotificationManager.class
                    );

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void stopPlayback() {

        releasePlayer();
        sendProgress();
    }

    private void releasePlayer() {

        handler.removeCallbacks(progressRunnable);

        prepared = false;

        if (player != null) {

            try {
                player.release();
            } catch (Exception ignored) {
            }

            player = null;
        }
    }

    @Override
    public void onDestroy() {

        releasePlayer();

        if (isForeground) {
            stopForeground(true);
            isForeground = false;
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    }
