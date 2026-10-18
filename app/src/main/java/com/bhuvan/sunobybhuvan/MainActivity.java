package com.bhuvan.sunobybhuvan;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.ClipData;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    private static final int PICK_AUDIO = 101;
    private static final int PICK_FOLDER = 102;

    private static final String PREFS = "SunoSettings";
    private static final String THEME_KEY = "theme";
    private static final String FOLDERS_KEY = "folders";

    private int theme = 0;
    private int backgroundColor;
    private int panelColor;
    private int accentColor;
    private int textColor;
    private int secondaryColor;

    private LinearLayout root;
    private TextView songName;
    private TextView status;
    private TextView currentTime;
    private TextView totalTime;
    private TextView themeLabel;
    private SeekBar seekBar;

    private final ArrayList<Uri> songs = new ArrayList<>();
    private final ArrayList<String> songNames = new ArrayList<>();
    private final ArrayList<Uri> folders = new ArrayList<>();

    private int songIndex = -1;
    private boolean seeking = false;
    private boolean receiverRegistered = false;

    private int duration = 0;
    private int position = 0;
    private boolean playing = false;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private final BroadcastReceiver progressReceiver =
            new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;

            position = intent.getIntExtra(
                    PlaybackService.EXTRA_POSITION, 0);

            duration = intent.getIntExtra(
                    PlaybackService.EXTRA_DURATION, 0);

            playing = intent.getBooleanExtra(
                    PlaybackService.EXTRA_PLAYING, false);

            boolean prepared = intent.getBooleanExtra(
                    PlaybackService.EXTRA_PREPARED, false);

            if (prepared) {
                if (!seeking) {
                    seekBar.setMax(Math.max(duration, 1));
                    seekBar.setProgress(
                            Math.min(position, Math.max(duration, 1)));
                }

                currentTime.setText(formatTime(position));
                totalTime.setText(formatTime(duration));

                status.setText(
                        playing ? "♫ Now playing" : "Ⅱ Paused");
            }
        }
    };

    private int dp(int value) {
        return (int) (value *
                getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        theme = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getInt(THEME_KEY, 0);

        applyTheme();
        createScreen();
        registerProgressReceiver();
        loadSavedFolders();
        scanSavedFolders();
    }

    private void registerProgressReceiver() {
        IntentFilter filter =
                new IntentFilter(PlaybackService.ACTION_PROGRESS);

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                    progressReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(progressReceiver, filter);
        }

        receiverRegistered = true;
    }

    private void applyTheme() {
        switch (theme) {
            case 1:
                backgroundColor = Color.rgb(24, 14, 39);
                panelColor = Color.rgb(55, 37, 78);
                accentColor = Color.rgb(181, 117, 255);
                textColor = Color.WHITE;
                secondaryColor = Color.rgb(211, 194, 230);
                break;

            case 2:
                backgroundColor = Color.rgb(8, 22, 38);
                panelColor = Color.rgb(23, 48, 72);
                accentColor = Color.rgb(45, 190, 255);
                textColor = Color.WHITE;
                secondaryColor = Color.rgb(185, 216, 238);
                break;

            case 3:
                backgroundColor = Color.rgb(40, 17, 29);
                panelColor = Color.rgb(72, 35, 53);
                accentColor = Color.rgb(255, 105, 155);
                textColor = Color.WHITE;
                secondaryColor = Color.rgb(240, 195, 215);
                break;

            case 4:
                backgroundColor = Color.rgb(10, 32, 26);
                panelColor = Color.rgb(24, 59, 47);
                accentColor = Color.rgb(56, 220, 145);
                textColor = Color.WHITE;
                secondaryColor = Color.rgb(180, 225, 205);
                break;

            default:
                backgroundColor = Color.rgb(12, 13, 19);
                panelColor = Color.rgb(34, 35, 46);
                accentColor = Color.rgb(153, 117, 255);
                textColor = Color.WHITE;
                secondaryColor = Color.LTGRAY;
                break;
        }
    }

    private String getThemeName() {
        switch (theme) {
            case 1: return "Royal Purple";
            case 2: return "Ocean Blue";
            case 3: return "Rose Pink";
            case 4: return "Emerald Green";
            default: return "Midnight Black";
        }
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private void createScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(backgroundColor);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(25), dp(22), dp(25));
        root.setBackgroundColor(backgroundColor);

        TextView brand = new TextView(this);
        brand.setText("♫  SUNO BY BHUVAN");
        brand.setTextColor(accentColor);
        brand.setTextSize(15);
        brand.setTypeface(null, Typeface.BOLD);
        root.addView(brand);

        TextView title = new TextView(this);
        title.setText("Your music.");
        title.setTextSize(32);
        title.setTextColor(textColor);
        title.setTypeface(null, Typeface.BOLD);

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(10);
        root.addView(title, titleParams);

        TextView subtitle = new TextView(this);
        subtitle.setText("Your vibe. Your rules.");
        subtitle.setTextSize(15);
        subtitle.setTextColor(secondaryColor);
        root.addView(subtitle);

        TextView icon = new TextView(this);
        icon.setText("♫");
        icon.setTextSize(76);
        icon.setGravity(Gravity.CENTER);
        icon.setTextColor(Color.WHITE);
        icon.setBackground(rounded(accentColor, 25));

        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(-1, dp(145));
        iconParams.topMargin = dp(22);
        root.addView(icon, iconParams);

        songName = new TextView(this);
        songName.setText("No song selected");
        songName.setTextSize(19);
        songName.setTextColor(textColor);
        songName.setTypeface(null, Typeface.BOLD);

        LinearLayout.LayoutParams nameParams =
                new LinearLayout.LayoutParams(-1, -2);
        nameParams.topMargin = dp(20);
        root.addView(songName, nameParams);

        status = new TextView(this);
        status.setText("Choose music from your phone.");
        status.setTextSize(14);
        status.setTextColor(secondaryColor);

        LinearLayout.LayoutParams statusParams =
                new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(6);
        root.addView(status, statusParams);

        seekBar = new SeekBar(this);
        seekBar.setMax(1);
        seekBar.setProgressTintList(
                ColorStateList.valueOf(accentColor));
        seekBar.setThumbTintList(
                ColorStateList.valueOf(accentColor));

        LinearLayout.LayoutParams seekParams =
                new LinearLayout.LayoutParams(-1, -2);
        seekParams.topMargin = dp(15);
        root.addView(seekBar, seekParams);

        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);

        currentTime = new TextView(this);
        currentTime.setText("00:00");
        currentTime.setTextColor(secondaryColor);

        totalTime = new TextView(this);
        totalTime.setText("00:00");
        totalTime.setTextColor(secondaryColor);
        totalTime.setGravity(Gravity.END);

        timeRow.addView(currentTime,
                new LinearLayout.LayoutParams(0, -2, 1));
        timeRow.addView(totalTime,
                new LinearLayout.LayoutParams(0, -2, 1));

        root.addView(timeRow);

        seekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(
                    SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    currentTime.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                seeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                Intent intent = new Intent(
                        MainActivity.this, PlaybackService.class);
                intent.setAction(PlaybackService.ACTION_SEEK);
                intent.putExtra(
                        PlaybackService.EXTRA_SEEK_POSITION,
                        bar.getProgress());
                startService(intent);
                seeking = false;
            }
        });

        addButton("＋  Choose Songs", panelColor,
                v -> chooseAudio(), 20);

        addButton("📁  Add Music Folder", panelColor,
                v -> chooseFolder(), 10);

        addButton("▶  Play", accentColor,
                v -> playSong(), 10);

        addButton("Ⅱ  Pause", panelColor,
                v -> pauseSong(), 10);

        LinearLayout navigation = new LinearLayout(this);
        navigation.setOrientation(LinearLayout.HORIZONTAL);

        Button previous = makeButton("⏮ Previous", panelColor);
        Button next = makeButton("Next ⏭", panelColor);

        LinearLayout.LayoutParams navParams =
                new LinearLayout.LayoutParams(0, dp(52), 1);
        navParams.setMargins(dp(4), dp(10), dp(4), 0);

        navigation.addView(previous, navParams);
        navigation.addView(next, navParams);
        root.addView(navigation);

        previous.setOnClickListener(v -> previousSong());
        next.setOnClickListener(v -> nextSong());

        addButton("🎨  Change Theme", panelColor,
                v -> changeTheme(), 18);

        themeLabel = new TextView(this);
        themeLabel.setText("Theme: " + getThemeName());
        themeLabel.setTextColor(secondaryColor);
        themeLabel.setGravity(Gravity.CENTER);
        themeLabel.setTextSize(12);

        LinearLayout.LayoutParams themeParams =
                new LinearLayout.LayoutParams(-1, -2);
        themeParams.topMargin = dp(10);
        root.addView(themeLabel, themeParams);

        TextView footer = new TextView(this);
        footer.setText("SUNO • YOUR PERSONAL MUSIC SPACE");
        footer.setTextColor(secondaryColor);
        footer.setGravity(Gravity.CENTER);
        footer.setTextSize(11);

        LinearLayout.LayoutParams footerParams =
                new LinearLayout.LayoutParams(-1, -2);
        footerParams.topMargin = dp(20);
        root.addView(footer, footerParams);

        scroll.addView(root);
        setContentView(scroll);
    }

    private void addButton(
            String text, int color,
            View.OnClickListener listener, int margin) {
        Button button = makeButton(text, color);
        button.setOnClickListener(listener);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, dp(52));
        params.topMargin = dp(margin);
        root.addView(button, params);
    }

    private Button makeButton(String text, int color) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(Color.WHITE);
        button.setBackground(rounded(color, 16));
        return button;
    }

    private void chooseAudio() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, PICK_AUDIO);
    }

    private void chooseFolder() {
        Intent intent = new Intent(
                Intent.ACTION_OPEN_DOCUMENT_TREE);

        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        startActivityForResult(intent, PICK_FOLDER);
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == PICK_AUDIO) {
            if (data.getClipData() != null) {
                ClipData clip = data.getClipData();

                for (int i = 0; i < clip.getItemCount(); i++) {
                    addSong(clip.getItemAt(i).getUri(), null);
                }
            } else if (data.getData() != null) {
                addSong(data.getData(), null);
            }

            updateSongDisplay();

        } else if (requestCode == PICK_FOLDER) {
            Uri folder = data.getData();
            if (folder == null) return;

            try {
                getContentResolver().takePersistableUriPermission(
                        folder, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }

            if (!folders.contains(folder)) {
                folders.add(folder);
                saveFolders();
            }

            int before = songs.size();
            scanFolder(folder);

            updateSongDisplay();

            Toast.makeText(this,
                    "Folder added • " + (songs.size() - before)
                            + " new songs",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void addSong(Uri uri, String suppliedName) {
        if (uri == null || songs.contains(uri)) return;

        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }

        String name = suppliedName;
        if (name == null || name.trim().isEmpty()) {
            name = getAudioName(uri);
        }

        songs.add(uri);
        songNames.add(name);

        if (songIndex < 0) songIndex = 0;
    }

    private String getAudioName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(
                uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null) return name;
                }
            }
        } catch (Exception ignored) {
        }

        return "Audio file";
    }

    private void scanFolder(Uri treeUri) {
        try {
            String documentId =
                    DocumentsContract.getTreeDocumentId(treeUri);
            scanDocument(treeUri, documentId);
        } catch (Exception e) {
            Toast.makeText(this,
                    "Could not read this folder",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void scanDocument(Uri treeUri, String documentId) {
        Uri childrenUri =
                DocumentsContract.buildChildDocumentsUriUsingTree(
                        treeUri, documentId);

        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };

        try (Cursor cursor = getContentResolver().query(
                childrenUri, projection, null, null, null)) {

            if (cursor == null) return;

            int idColumn = cursor.getColumnIndex(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameColumn = cursor.getColumnIndex(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeColumn = cursor.getColumnIndex(
                    DocumentsContract.Document.COLUMN_MIME_TYPE);

            while (cursor.moveToNext()) {
                String childId = cursor.getString(idColumn);
                String name = cursor.getString(nameColumn);
                String mime = cursor.getString(mimeColumn);

                if (DocumentsContract.Document.MIME_TYPE_DIR
                        .equals(mime)) {
                    scanDocument(treeUri, childId);
                } else if (isAudioFile(name, mime)) {
                    Uri fileUri =
                            DocumentsContract.buildDocumentUriUsingTree(
                                    treeUri, childId);
                    addSong(fileUri, name);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private boolean isAudioFile(String name, String mime) {
        if (mime != null && mime.startsWith("audio/")) {
            return true;
        }

        if (name == null) return false;

        String lower = name.toLowerCase(Locale.ROOT);

        return lower.endsWith(".mp3")
                || lower.endsWith(".m4a")
                || lower.endsWith(".aac")
                || lower.endsWith(".wav")
                || lower.endsWith(".ogg")
                || lower.endsWith(".opus")
                || lower.endsWith(".flac")
                || lower.endsWith(".wma")
                || lower.endsWith(".3gp");
    }

    private void saveFolders() {
        Set<String> saved = new HashSet<>();

        for (Uri uri : folders) {
            saved.add(uri.toString());
        }

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putStringSet(FOLDERS_KEY, saved)
                .apply();
    }

    private void loadSavedFolders() {
        Set<String> saved =
                getSharedPreferences(PREFS, MODE_PRIVATE)
                        .getStringSet(FOLDERS_KEY,
     
