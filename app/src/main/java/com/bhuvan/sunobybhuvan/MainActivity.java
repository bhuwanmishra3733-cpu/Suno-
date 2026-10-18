package com.bhuvan.sunobybhuvan;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    private static final int PICK_AUDIO = 101;
    private static final int PICK_FOLDER = 102;

    private final ArrayList<Uri> songs = new ArrayList<>();
    private final ArrayList<String> songNames = new ArrayList<>();

    private final Set<String> knownSongs = new HashSet<>();

    private final LinkedHashSet<String> folderUris =
            new LinkedHashSet<>();

    private final LinkedHashMap<String, String> folderNames =
            new LinkedHashMap<>();

    private LinearLayout root;
    private LinearLayout songList;
    private LinearLayout folderList;

    private TextView currentSongText;
    private TextView statusText;
    private TextView elapsedText;
    private TextView durationText;
    private TextView countText;

    private SeekBar seekBar;
    private ScrollView scrollView;

    private int currentIndex = -1;
    private int currentPosition = 0;
    private int currentDuration = 0;
    private int themeIndex = 0;

    private boolean userSeeking = false;
    private boolean receiverRegistered = false;

    private SharedPreferences prefs;

    // Five complete colour palettes:
    // Midnight Black, Royal Purple, Ocean Blue,
    // Rose Pink, Emerald Green.

    private final int[] backgrounds = {
            Color.rgb(10, 10, 16),
            Color.rgb(24, 12, 43),
            Color.rgb(8, 22, 38),
            Color.rgb(38, 13, 29),
            Color.rgb(8, 28, 22)
    };

    private final int[] cards = {
            Color.rgb(27, 25, 39),
            Color.rgb(43, 27, 68),
            Color.rgb(17, 39, 59),
            Color.rgb(59, 27, 45),
            Color.rgb(18, 45, 35)
    };

    private final int[] accents = {
            Color.rgb(190, 115, 255),
            Color.rgb(192, 132, 252),
            Color.rgb(56, 189, 248),
            Color.rgb(255, 111, 166),
            Color.rgb(52, 211, 153)
    };

    private final int[] foregrounds = {
            Color.WHITE,
            Color.WHITE,
            Color.WHITE,
            Color.WHITE,
            Color.WHITE
    };

    private final String[] themeNames = {
            "Midnight Black",
            "Royal Purple",
            "Ocean Blue",
            "Rose Pink",
            "Emerald Green"
    };

    private final BroadcastReceiver progressReceiver =
            new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (!PlaybackService.ACTION_PROGRESS.equals(
                    intent.getAction())) {
                return;
            }

            currentPosition = intent.getIntExtra(
                    PlaybackService.EXTRA_POSITION, 0);

            currentDuration = intent.getIntExtra(
                    PlaybackService.EXTRA_DURATION, 0);

            boolean prepared = intent.getBooleanExtra(
                    PlaybackService.EXTRA_PREPARED, false);

            boolean playing = intent.getBooleanExtra(
                    PlaybackService.EXTRA_PLAYING, false);

            if (seekBar != null && !userSeeking) {
                seekBar.setMax(Math.max(1, currentDuration));

                seekBar.setProgress(
                        Math.min(currentPosition, currentDuration));
            }

            if (elapsedText != null) {
                elapsedText.setText(formatTime(currentPosition));
            }

            if (durationText != null) {
                durationText.setText(formatTime(currentDuration));
            }

            if (statusText != null && prepared) {
                statusText.setText(
                        playing ? "▶  NOW PLAYING" : "Ⅱ  PAUSED");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(
                "suno_settings", MODE_PRIVATE);

        themeIndex = prefs.getInt("theme", 0);

        loadSavedFolders();
        registerProgressReceiver();

        createScreen();

        // Restore the saved music folders.
        scanSavedFolders();
    }

    private void registerProgressReceiver() {

        IntentFilter filter = new IntentFilter(
                PlaybackService.ACTION_PROGRESS);

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

    private int bg() {
        return backgrounds[themeIndex];
    }

    private int card() {
        return cards[themeIndex];
    }

    private int accent() {
        return accents[themeIndex];
    }

    private int fg() {
        return foregrounds[themeIndex];
    }

    private void createScreen() {

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(bg());

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(30));

        scrollView.addView(root);

        // Brand

        TextView brand = text("♫  SUNO", 16, accent(), true);
        brand.setGravity(Gravity.CENTER);
        root.addView(brand, matchWrap());

        TextView title = text(
                "Suno by Bhuvan", 29, fg(), true);

        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(8), 0, dp(3));

        root.addView(title, matchWrap());

        TextView subtitle = text(
                "YOUR MUSIC. YOUR VIBE.", 11, accent(), true);

        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 0, 0, dp(22));

        root.addView(subtitle, matchWrap());

        // Player card

        LinearLayout playerCard = new LinearLayout(this);
        playerCard.setOrientation(LinearLayout.VERTICAL);
        playerCard.setGravity(Gravity.CENTER);
        playerCard.setPadding(
                dp(18), dp(22), dp(18), dp(20));

        playerCard.setBackground(
                rounded(card(), dp(22)));

        LinearLayout.LayoutParams playerParams = matchWrap();
        playerParams.bottomMargin = dp(18);

        root.addView(playerCard, playerParams);

        TextView musicIcon = text(
                "♫", 76, accent(), true);

        musicIcon.setGravity(Gravity.CENTER);
        musicIcon.setPadding(0, 0, 0, dp(12));

        playerCard.addView(musicIcon, matchWrap());

        currentSongText = text(
                "Choose a song to get started", 18, fg(), true);

        currentSongText.setGravity(Gravity.CENTER);
        currentSongText.setMaxLines(2);

        playerCard.addView(currentSongText, matchWrap());

        statusText = text(
                "READY TO PLAY", 11, accent(), true);

        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, dp(9), 0, dp(16));

        playerCard.addView(statusText, matchWrap());

        // Seek bar

        seekBar = new SeekBar(this);
        seekBar.setMax(1);
        seekBar.setProgress(0);

        seekBar.setProgressTintList(
                ColorStateList.valueOf(accent()));

        seekBar.setThumbTintList(
                ColorStateList.valueOf(accent()));

        playerCard.addView(seekBar, matchWrap());

        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);

        elapsedText = text("00:00", 12, fg(), false);
        durationText = text("00:00", 12, fg(), false);

        timeRow.addView(elapsedText,
                new LinearLayout.LayoutParams(0, dp(28), 1));

        durationText.setGravity(Gravity.END);

        timeRow.addView(durationText,
                new LinearLayout.LayoutParams(0, dp(28), 1));

        playerCard.addView(timeRow, matchWrap());

        seekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(
                    SeekBar bar, int progress, boolean fromUser) {

                if (fromUser && elapsedText != null) {
                    elapsedText.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {

                userSeeking = false;

                if (currentIndex >= 0 && currentDuration > 0) {

                    Intent intent = new Intent(
                            MainActivity.this,
                            PlaybackService.class);

                    intent.setAction(PlaybackService.ACTION_SEEK);

                    intent.putExtra(
                            PlaybackService.EXTRA_SEEK_POSITION,
                            bar.getProgress());

                    startService(intent);
                }
            }
        });

        // Playback controls

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);

        Button previous = button("⏮", false);
        Button play = button("▶  PLAY", true);
        Button pause = button("Ⅱ  PAUSE", false);
        Button next = button("⏭", false);

        previous.setOnClickListener(v -> playPrevious());
        play.setOnClickListener(v -> playCurrent());
        pause.setOnClickListener(v -> pauseCurrent());
        next.setOnClickListener(v -> playNext());

        addControl(controls, previous, 1);
        addControl(controls, play, 2);
        addControl(controls, pause, 2);
        addControl(controls, next, 1);

        playerCard.addView(controls, matchWrap());

        // Library actions

        addSectionTitle(root, "MUSIC LIBRARY");

        countText = text("0 songs added", 12, accent(), false);
        countText.setPadding(0, 0, 0, dp(10));

        root.addView(countText, matchWrap());

        addButton(root, "＋  Add Music Folder", true,
                v -> chooseFolder());

        addButton(root, "♫  Choose Individual Songs", false,
                v -> chooseAudio());

        // Selected folders

        addSectionTitle(root, "YOUR FOLDERS");

        folderList = new LinearLayout(this);
        folderList.setOrientation(LinearLayout.VERTICAL);
        root.addView(folderList, matchWrap());

        // Song list

        addSectionTitle(root, "ALL SONGS");

        songList = new LinearLayout(this);
        songList.setOrientation(LinearLayout.VERTICAL);
        root.addView(songList, matchWrap());

        // Theme

        addSectionTitle(root, "PERSONALISE YOUR PLAYER");

        TextView currentTheme = text(
                "Current theme: " + themeNames[themeIndex],
                13, fg(), false);

        currentTheme.setGravity(Gravity.CENTER);
        currentTheme.setPadding(0, 0, 0, dp(8));

        root.addView(currentTheme, matchWrap());

        addButton(root, "🎨  Change Theme", true, v -> {

            themeIndex = (themeIndex + 1) % themeNames.length;

            prefs.edit().putInt("theme", themeIndex).apply();

            createScreen();
            refreshSongList();
            refreshFolderList();
            updateSongDisplay();
            updateProgressDisplay();
        });

        setContentView(scrollView);

        refreshSongList();
        refreshFolderList();
        updateSongDisplay();
        updateProgressDisplay();
    }

    private void addSectionTitle(
            LinearLayout parent, String title) {

        TextView heading = text(title, 14, accent(), true);
        heading.setPadding(0, dp(20), 0, dp(10));

        parent.addView(heading, matchWrap());
    }

    private TextView text(
            String value, float size, int color, boolean bold) {

        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);

        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }

        return view;
    }

    private Button button(String label, boolean primary) {

        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setTextColor(primary ? bg() : fg());

        b.setBackground(rounded(
                primary ? accent() : card(), dp(15)));

        b.setPadding(dp(5), 0, dp(5), 0);

        return b;
    }

    private void addButton(
            LinearLayout parent,
            String label,
            boolean primary,
            View.OnClickListener listener) {

        Button b = button(label, primary);
        b.setTextSize(14);
        b.setOnClickListener(listener);

        LinearLayout.LayoutParams params = matchWrap();
        params.height = dp(52);
        params.setMargins(0, dp(5), 0, dp(5));

        parent.addView(b, params);
    }

    private void addControl(
            LinearLayout parent, Button button, int weight) {

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(0, dp(48), weight);

        params.setMargins(dp(2), 0, dp(2), 0);
        parent.addView(button, params);
    }

    private GradientDrawable rounded(int color, int radius) {

        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);

        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {

        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {

        return (int) (value * getResources()
                .getDisplayMetrics().density + 0.5f);
    }

    // ---------------------------------
    // SONG AND FOLDER PICKERS
    // ---------------------------------

    private void chooseAudio() {

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("audio/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

        startActivityForResult(intent, PICK_AUDIO);
    }

    private void chooseFolder() {

        Intent intent = new Intent(
                Intent.ACTION_OPEN_DOCUMENT_TREE);

        startActivityForResult(intent, PICK_FOLDER);
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null) {
            return;
        }

        if (requestCode == PICK_AUDIO) {

            int before = songs.size();

            try {

                if (data.getClipData() != null) {

                    ClipData clip = data.getClipData();

                    for (int i = 0; i < clip.getItemCount(); i++) {

                        Uri uri = clip.getItemAt(i).getUri();

                        saveReadPermission(uri, data);
                        addSong(uri);
                    }

                } else if (data.getData() != null) {

                    Uri uri = data.getData();

                    saveReadPermission(uri, data);
                    addSong(uri);
                }

            } catch (Exception e) {

                toast("Some songs could not be added");
            }

            refreshSongList();
            updateSongDisplay();

            toast((songs.size() - before) + " new song(s) added");
        }

        if (requestCode == PICK_FOLDER) {

            Uri treeUri = data.getData();

            if (treeUri == null) {
                return;
            }

            String key = treeUri.toString();

            if (folderUris.contains(key)) {

                toast("This folder is already added");
                return;
            }

            try {

                int flags = data.getFlags()
                        & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                getContentResolver().takePersistableUriPermission(
                        treeUri, flags);

                folderUris.add(key);
                folderNames.put(key, getFolderName(treeUri));

                saveFolders();
                refreshFolderList();

                statusText.setText("Scanning folder...");

                scanFolder(treeUri);

            } catch (Exception e) {

                toast("Could not access this folder");
            }
        }
    }

    private void saveReadPermission(Uri uri, Intent data) {

        try {

            int flags = data.getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            getContentResolver().takePersistableUriPermission(
                    uri, flags);

        } catch (Exception ignored) {
        }
    }

    // ---------------------------------
    // MULTIPLE FOLDER STORAGE
    // ---------------------------------

    private void loadSavedFolders() {

        Set<String> saved = prefs.getStringSet(
                "folder_uris", new HashSet<String>());

        if (saved != null) {

            folderUris.addAll(saved);

            for (String value : folderUris) {

                Uri uri = Uri.parse(value);

                folderNames.put(value, getFolderName(uri));
            }
        }
    }

    private void saveFolders() {

        prefs.edit()
                .putStringSet(
                        "folder_uris",
                        new HashSet<>(folderUris))
                .apply();
    }

    private void scanSavedFolders() {

        for (String value : new ArrayList<>(folderUris)) {

            try {

                scanDocument(
                        Uri.parse(value),
                        DocumentsContract.getTreeDocumentId(
                                Uri.parse(value)),
                        0);

            } catch (Exception ignored) {
            }
        }

        refreshFolderList();
        refreshSongList();
        updateSongDisplay();
        updateProgressDisplay();

        if (songs.size() > 0 && statusText != null) {
            statusText.setText(
                    songs.size() + " songs ready");
        }
    }

    private String getFolderName(Uri treeUri) {

        Cursor cursor = null;

        try {

            String id = DocumentsContract.getTreeDocumentId(
                    treeUri);

            Uri documentUri =
                    DocumentsContract.buildDocumentUriUsingTree(
                            treeUri, id);

                    documentUri,
                    new String[]{
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME
                    },
                    null,
                    null,
                    null
            );

            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME
                );

                if (column >= 0) {
                    String name = cursor.getString(column);

                    if (name != null && !name.isEmpty()) {
                        return name;
                    }
                }
            }

        } catch (Exception ignored) {
            // Folder name could not be read.
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return "Music Folder";
    }

    private void refreshFolderList() {

        if (folderList == null) {
            return;
        }

        folderList.removeAllViews();

        if (folderUris.isEmpty()) {
            TextView empty = text(
                    "No folders added yet.",
                    13,
                    fg(),
                    false
            );

            folderList.addView(empty, matchWrap());
            return;
        }

        for (String key : folderUris) {

            String name = folderNames.get(key);

            if (name == null) {
                name = "Music Folder";
            }

            TextView folder = text(
                    "📁  " + name,
                    13,
                    fg(),
                    false
            );

            folder.setPadding(
                    dp(12), dp(12), dp(12), dp(12)
            );

            folder.setBackground(
                    rounded(card(), dp(12))
            );

            LinearLayout.LayoutParams params = matchWrap();
            params.setMargins(0, 0, 0, dp(7));

            folderList.addView(folder, params);
        }
    }

    @Override
    protected void onDestroy() {

        if (receiverRegistered) {
            try {
                unregisterReceiver(progressReceiver);
            } catch (Exception ignored) {
                // Receiver may already be unregistered.
            }

            receiverRegistered = false;
        }

        super.onDestroy();
    }
        }
