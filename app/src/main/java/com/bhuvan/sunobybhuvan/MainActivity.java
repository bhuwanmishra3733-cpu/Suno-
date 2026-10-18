package com.bhuvan.sunobybhuvan;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;

public class MainActivity extends Activity {

    private static final int PICK_AUDIO = 101;

    private MediaPlayer player;
    private Uri audioUri;

    private boolean prepared = false;

    private TextView songName;
    private TextView status;
    private Button playButton;

    private int dp(int value) {
        return (int) (
            value * getResources().getDisplayMetrics().density + 0.5f
        );
    }

    private android.graphics.drawable.GradientDrawable rounded(
            int color, int radius) {

        android.graphics.drawable.GradientDrawable drawable =
                new android.graphics.drawable.GradientDrawable();

        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));

        return drawable;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createScreen();
    }

    private void createScreen() {

        int background = Color.rgb(15, 17, 25);
        int panel = Color.rgb(34, 36, 50);
        int purple = Color.rgb(112, 82, 255);

        LinearLayout root = new LinearLayout(this);

        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(
                dp(24), dp(30), dp(24), dp(24)
        );
        root.setBackgroundColor(background);

        TextView brand = new TextView(this);

        brand.setText("♫  SUNO");
        brand.setTextSize(15);
        brand.setTextColor(Color.rgb(190, 177, 255));

        root.addView(brand);

        TextView title = new TextView(this);

        title.setText("Suno by Bhuvan");
        title.setTextSize(30);
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, 1);

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(-1, -2);

        titleParams.topMargin = dp(16);

        root.addView(title, titleParams);

        TextView subtitle = new TextView(this);

        subtitle.setText("Your music. Your vibe.");
        subtitle.setTextSize(15);
        subtitle.setTextColor(Color.LTGRAY);

        root.addView(subtitle);

        TextView musicIcon = new TextView(this);

        musicIcon.setText("♫");
        musicIcon.setTextSize(80);
        musicIcon.setTextColor(Color.WHITE);
        musicIcon.setGravity(Gravity.CENTER);
        musicIcon.setBackground(rounded(purple, 28));

        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(-1, dp(180));

        iconParams.topMargin = dp(30);

        root.addView(musicIcon, iconParams);

        songName = new TextView(this);

        songName.setText("No song selected");
        songName.setTextSize(20);
        songName.setTextColor(Color.WHITE);
        songName.setTypeface(null, 1);

        LinearLayout.LayoutParams songParams =
                new LinearLayout.LayoutParams(-1, -2);

        songParams.topMargin = dp(24);

        root.addView(songName, songParams);

        status = new TextView(this);

        status.setText("Choose a song from your phone.");
        status.setTextSize(14);
        status.setTextColor(Color.LTGRAY);

        LinearLayout.LayoutParams statusParams =
                new LinearLayout.LayoutParams(-1, -2);

        statusParams.topMargin = dp(8);

        root.addView(status, statusParams);

        Button chooseButton =
                createButton("＋  Choose music from phone", panel);

        chooseButton.setOnClickListener(v -> chooseAudio());

        LinearLayout.LayoutParams chooseParams =
                new LinearLayout.LayoutParams(-1, dp(54));

        chooseParams.topMargin = dp(24);

        root.addView(chooseButton, chooseParams);

        playButton = createButton("▶  Play", panel);

        playButton.setEnabled(false);
        playButton.setOnClickListener(v -> togglePlayback());

        LinearLayout.LayoutParams playParams =
                new LinearLayout.LayoutParams(-1, dp(54));

        playParams.topMargin = dp(12);

        root.addView(playButton, playParams);

        TextView footer = new TextView(this);

        footer.setText(
                "SUNO • VERSION 1.0\nYour personal music space"
        );

        footer.setTextSize(12);
        footer.setTextColor(Color.rgb(160, 163, 176));
        footer.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams footerParams =
                new LinearLayout.LayoutParams(-1, -2);

        footerParams.topMargin = dp(28);

        root.addView(footer, footerParams);

        setContentView(root);
    }

    private Button createButton(String text, int color) {

        Button button = new Button(this);

        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTextColor(Color.WHITE);
        button.setBackground(rounded(color, 18));

        return button;
    }

    private void chooseAudio() {

        Intent intent =
                new Intent(Intent.ACTION_OPEN_DOCUMENT);

        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");

        startActivityForResult(intent, PICK_AUDIO);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(
                requestCode, resultCode, data
        );

        if (requestCode == PICK_AUDIO
                && resultCode == RESULT_OK
                && data != null
                && data.getData() != null) {

            audioUri = data.getData();

            try {
                getContentResolver()
                        .takePersistableUriPermission(
                                audioUri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        );
            } catch (Exception ignored) {
            }

            songName.setText(getAudioName(audioUri));

            status.setText("Song selected. Preparing audio...");

            prepareAudio();
        }
    }

    private String getAudioName(Uri uri) {

        try (Cursor cursor =
                     getContentResolver().query(
                             uri, null, null, null, null)) {

            if (cursor != null && cursor.moveToFirst()) {

                int index = cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                );

                if (index >= 0) {
                    return cursor.getString(index);
                }
            }

        } catch (Exception ignored) {
        }

        return "Selected audio";
    }

    private void prepareAudio() {

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

            player.setDataSource(this, audioUri);

            player.setOnPreparedListener(mp -> {

                prepared = true;

                playButton.setEnabled(true);
                playButton.setText("▶  Play");

                status.setText("Ready to play.");
            });

            player.setOnCompletionListener(mp -> {

                playButton.setText("▶  Play");
                status.setText("Song finished.");
            });

            player.setOnErrorListener((mp, what, extra) -> {

                status.setText(
                        "Unable to play this audio file."
                );

                releasePlayer();

                playButton.setEnabled(false);

                return true;
            });

            player.prepareAsync();

        } catch (IOException | IllegalStateException error) {

            status.setText("Couldn't open this audio file.");

            releasePlayer();
        }
    }

    private void togglePlayback() {

        if (!prepared || player == null) {
            return;
        }

        try {

            if (player.isPlaying()) {

                player.pause();

                playButton.setText("▶  Play");
                status.setText("Paused");

            } else {

                player.start();

                playButton.setText("Ⅱ  Pause");
                status.setText("Playing");
            }

        } catch (IllegalStateException error) {

            status.setText("Please select the song again.");
        }
    }

    private void releasePlayer() {

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
    protected void onDestroy() {

        releasePlayer();

        super.onDestroy();
    }
}
