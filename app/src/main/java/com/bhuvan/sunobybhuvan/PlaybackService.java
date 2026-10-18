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

        } else if (ACTION_SEEK.equals(action))
