package com.bhuvan.sunobybhuvan;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int PICK_AUDIO = 101;
    private static final int PICK_FOLDERS = 102;

    private static final String PREFS = "SunoSettings";
    private static final String THEME_KEY = "theme";

    private static final int PURPLE = 0;
    private static final int RED = 1;
    private static final int BLUE = 2;
    private static final int LIGHT = 3;

    private int currentTheme = PURPLE;

    private int backgroundColor;
    private int panelColor;
    private int accentColor;
    private int textColor;
    private int secondaryTextColor;

    private LinearLayout root;
    private TextView songName;
    private TextView status;
    private TextView currentTime;
    private TextView totalTime;
    private TextView themeLabel;
    private SeekBar seekBar;

    private Button playButton;
    private Button pauseButton;

    private final ArrayList<Uri> selectedSongs = new ArrayList<>();
    private final ArrayList<String> selectedSongNames = new ArrayList<>();
    private final ArrayList<Uri> selectedFolders = new ArrayList<>();

    private int currentSongIndex = -1;

    private boolean isSeeking = false;

    private final android.os.Handler handler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    private final Runnable updateSeekBar = new Runnable() {
        @Override
        public void run() {
            // Seek bar integration will be completed with
            // a playback service that exposes player position.
            handler.postDelayed(this, 500);
        }
    };

    private int dp(int value) {
        return (int) (
                value * getResources().getDisplayMetrics().density
                        + 0.5f
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        currentTheme = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getInt(THEME_KEY, PURPLE);

        applyTheme();
        createScreen();
    }

    private void applyTheme() {

        switch (currentTheme) {

            case RED:
                backgroundColor = Color.rgb(22, 12, 18);
                panelColor = Color.rgb(55, 27, 38);
                accentColor = Color.rgb(255, 65, 95);
                textColor = Color.WHITE;
                secondaryTextColor = Color.rgb(215, 190, 200);
                break;

            case BLUE:
                backgroundColor = Color.rgb(8, 18, 32);
                panelColor = Color.rgb(20, 42, 65);
                accentColor = Color.rgb(35, 190, 255);
                textColor = Color.WHITE;
                secondaryTextColor = Color.rgb(185, 215, 235);
                break;

            case LIGHT:
                backgroundColor = Color.rgb(246, 245, 252);
                panelColor = Color.rgb(228, 225, 242);
                accentColor = Color.rgb(112, 82, 255);
                textColor = Color.rgb(30, 28, 45);
                secondaryTextColor = Color.rgb(95, 92, 110);
                break;

            default:
                backgroundColor = Color.rgb(15, 17, 25);
                panelColor = Color.rgb(34, 36, 50);
                accentColor = Color.rgb(112, 82, 255);
                textColor = Color.WHITE;
                secondaryTextColor = Color.LTGRAY;
                break;
        }
    }

    private GradientDrawable rounded(int color, int radius) {

        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));

        return drawable;
    }

    private void createScreen() {

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(25), dp(22), dp(24));
        root.setBackgroundColor(backgroundColor);

        TextView brand = new TextView(this);
        brand.setText("♫  SUNO BY BHUVAN");
        brand.setTextSize(15);
        brand.setTextColor(accentColor);
        brand.setTypeface(null, Typeface.BOLD);

        root.addView(brand);

        TextView title = new TextView(this);
        title.setText("Your music.");
        title.setTextSize(32);
        title.setTextColor(textColor);
        title.setTypeface(null, Typeface.BOLD);

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(-1, -2);

        titleParams.topMargin = dp(12);
        root.addView(title, titleParams);

        TextView subtitle = new TextView(this);
        subtitle.setText("Your vibe. Your rules.");
        subtitle.setTextSize(15);
        subtitle.setTextColor(secondaryTextColor);

        root.addView(subtitle);

        TextView musicIcon = new TextView(this);
        musicIcon.setText("♫");
        musicIcon.setTextSize(76);
        musicIcon.setGravity(Gravity.CENTER);
        musicIcon.setTextColor(Color.WHITE);
        musicIcon.setBackground(rounded(accentColor, 28));

        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(-1, dp(155));

        iconParams.topMargin = dp(24);
        root.addView(musicIcon, iconParams);

        songName = new TextView(this);
        songName.setText("No song selected");
        songName.setTextSize(19);
        songName.setTextColor(textColor);
        songName.setTypeface(null, Typeface.BOLD);

        LinearLayout.LayoutParams songParams =
                new LinearLayout.LayoutParams(-1, -2);

        songParams.topMargin = dp(22);
        root.addView(songName, songParams);

        status = new TextView(this);
        status.setText("Choose music from your phone.");
        status.setTextSize(14);
        status.setTextColor(secondaryTextColor);

        LinearLayout.LayoutParams statusParams =
                new LinearLayout.LayoutParams(-1, -2);

        statusParams.topMargin = dp(6);
        root.addView(status, statusParams);

        seekBar = new SeekBar(this);
        seekBar.setMax(1000);
        seekBar.setProgressTintList(
                android.content.res.ColorStateList.valueOf(accentColor)
        );
        seekBar.setThumbTintList(
                android.content.res.ColorStateList.valueOf(accentColor)
        );

        LinearLayout.LayoutParams seekParams =
                new LinearLayout.LayoutParams(-1, -2);

        seekParams.topMargin = dp(16);
        root.addView(seekBar, seekParams);

        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);

        currentTime = new TextView(this);
        currentTime.setText("00:00");
        currentTime.setTextColor(secondaryTextColor);

        totalTime = new TextView(this);
        totalTime.setText("00:00");
        totalTime.setGravity(Gravity.END);
        totalTime.setTextColor(secondaryTextColor);

        timeRow.addView(
                currentTime,
                new LinearLayout.LayoutParams(0, -2, 1)
        );

        timeRow.addView(
                totalTime,
                new LinearLayout.LayoutParams(0, -2, 1)
        );

        root.addView(timeRow);

        Button chooseButton =
                createButton("＋  Choose a song", panelColor);

        chooseButton.setOnClickListener(v -> chooseAudio());

        addButton(chooseButton, 20);

        Button folderButton =
                createButton("📁  Select music folders", panelColor);

        folderButton.setOnClickListener(v -> chooseFolders());

        addButton(folderButton, 10);

        playButton = createButton("▶  Play", accentColor);
        playButton.setOnClickListener(v -> playCurrentSong());

        addButton(playButton, 10);

        pauseButton = createButton("Ⅱ  Pause", panelColor);
        pauseButton.setOnClickListener(v -> pauseCurrentSong());

        addButton(pauseButton, 10);

        LinearLayout navigation = new LinearLayout(this);
        navigation.setOrientation(LinearLayout.HORIZONTAL);

        Button previousButton =
                createButton("⏮ Previous", panelColor);

        previousButton.setOnClickListener(v -> previousSong());

        Button nextButton =
                createButton("Next ⏭", panelColor);

        nextButton.setOnClickListener(v -> nextSong());

        LinearLayout.LayoutParams navParams =
                new LinearLayout.LayoutParams(0, dp(52), 1);

        navParams.setMargins(dp(4), dp(10), dp(4), 0);

        navigation.addView(previousButton, navParams);
        navigation.addView(nextButton, navParams);

        root.addView(navigation);

        Button themeButton =
                createButton("🎨  Change theme", panelColor);

        themeButton.setOnClickListener(v -> cycleTheme());

        addButton(themeButton, 18);

        themeLabel = new TextView(this);
        themeLabel.setText("Theme: " + getThemeName());
        themeLabel.setTextColor(secondaryTextColor);
        themeLabel.setTextSize(12);
        themeLabel.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams themeParams =
                new LinearLayout.LayoutParams(-1, -2);

        themeParams.topMargin = dp(10);
        root.addView(themeLabel, themeParams);

        TextView footer = new TextView(this);
        footer.setText("SUNO • YOUR PERSONAL MUSIC SPACE");
        footer.setTextSize(11);
        footer.setTextColor(secondaryTextColor);
        footer.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams footerParams =
                new LinearLayout.LayoutParams(-1, -2);

        footerParams.topMargin = dp(20);
        root.addView(footer, footerParams);

        setContentView(root);
    }

    private void addButton(Button button, int topMargin) {

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, dp(52));

        params.topMargin = dp(topMargin);
        root.addView(button, params);
    }

    private Button createButton(String text, int color) {

        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(
                currentTheme == LIGHT && color != accentColor
                        ? textColor : Color.WHITE
        );
        button.setBackground(rounded(color, 17));

        return button;
    }

    private void chooseAudio() {

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

        startActivityForResult(intent, PICK_AUDIO);
    }

    private void chooseFolders() {

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);

        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );

        startActivityForResult(intent, PICK_FOLDERS);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null) {
            return;
        }

        if (requestCode == PICK_AUDIO) {

            if (data.getClipData() != null) {

                android.content.ClipData clip = data.getClipData();

                for (int i = 0; i < clip.getItemCount(); i++) {
                    addSong(clip.getItemAt(i).getUri());
                }

            } else if (data.getData() != null) {

                addSong(data.getData());
            }

            updateSongDisplay();

        } else if (requestCode == PICK_FOLDERS) {

            Uri folderUri = data.getData();

            if (folderUri != null) {

                try {
                    getContentResolver().takePersistableUriPermission(
                            folderUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );
                } catch (Exception ignored) {
                }

                if (!selectedFolders.contains(folderUri)) {
                    selectedFolders.add(folderUri);
                }

                status.setText(
                        selectedFolders.size()
                                + " folder(s) selected. Folder scanning is not implemented yet."
                );

                Toast.makeText(
                        this,
                        "Folder permission saved",
                        Toast.LENGTH_SHORT
                ).show();
            }
        }
    }

    private void addSong(Uri uri) {

        if (uri == null || selectedSongs.contains(uri)) {
            return;
        }

        try {
            getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (Exception ignored) {
        }

        selectedSongs.add(uri);
        selectedSongNames.add(getAudioName(uri));

        if (currentSongIndex == -1) {
            currentSongIndex = 0;
        }
    }

    private String getAudioName(Uri uri) {

        try (Cursor cursor = getContentResolver().query(
                uri, null, null, null, null)) {

            if (cursor != null && cursor.moveToFirst()) {

                int index = cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                );

                if (index >= 0) {
                    String name = cursor.getString(index);

                    if (name != null) {
                        return name;
                    }
                }
            }

        } catch (Exception ignored) {
        }

        return "Selected audio";
    }

    private void updateSongDisplay() {

        if (selectedSongs.isEmpty()) {
            songName.setText("No song selected");
            status.setText("Choose a song from your phone.");
            return;
        }

        if (currentSongIndex < 0
                || currentSongIndex >= selectedSongNames.size()) {
            currentSongIndex = 0;
        }

        songName.setText(selectedSongNames.get(currentSongIndex));

        status.setText(
                selectedSongs.size()
                        + " song(s) selected. Tap Play to start."
        );
    }

    private void playCurrentSong() {

        if (selectedSongs.isEmpty() || currentSongIndex < 0) {
            chooseAudio();
            return;
        }

        Uri uri = selectedSongs.get(currentSongIndex);

        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(PlaybackService.ACTION_PLAY);
        intent.putExtra(
                PlaybackService.EXTRA_AUDIO_URI,
                uri.toString()
        );
        intent.putExtra(
                PlaybackService.EXTRA_SONG_NAME,
                selectedSongNames.get(currentSongIndex)
        );

        try {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }

            songName.setText(selectedSongNames.get(currentSongIndex));
            status.setText("Starting playback...");

        } catch (Exception e) {

            status.setText("Couldn't start playback.");
            Toast.makeText(
                    this,
                    "Playback could not start.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void pauseCurrentSong() {

        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(PlaybackService.ACTION_PAUSE);

        startService(intent);

        status.setText("Pause requested");
    }

    private void nextSong() {

        if (selectedSongs.isEmpty()) {
            chooseAudio();
            return;
        }

        currentSongIndex++;

        if (currentSongIndex >= selectedSongs.size()) {
            currentSongIndex = 0;
        }

        updateSongDisplay();
        playCurrentSong();
    }

    private void previousSong() {

        if (selectedSongs.isEmpty()) {
            chooseAudio();
            return;
        }

        currentSongIndex--;

        if (currentSongIndex < 0) {
            currentSongIndex = selectedSongs.size() - 1;
        }

        updateSongDisplay();
        playCurrentSong();
    }

    private void cycleTheme() {

        currentTheme = (currentTheme + 1) % 4;

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putInt(THEME_KEY, currentTheme)
                .apply();

        applyTheme();
        createScreen();

        Toast.makeText(
                this,
                "Theme: " + getThemeName(),
                Toast.LENGTH_SHORT
        ).show();
    }

    private String getThemeName() {

        switch (currentTheme) {
            case RED:
                return "Red Pulse";

            case BLUE:
                return "Ocean Blue";

            case LIGHT:
                return "Light Mode";

            default:
                return "Purple Neon";
        }
    }

    private String formatTime(int milliseconds) {

        int totalSeconds = Math.max(0, milliseconds / 1000);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;

        return String.format(
                Locale.getDefault(),
                "%02d:%02d",
                minutes,
                seconds
        );
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(updateSeekBar);
        super.onDestroy();
    }
}
    
