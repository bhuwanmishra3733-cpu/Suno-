package com.bhuvan.sunobybhuvan;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ClipData;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.graphics.Color;
import android.graphics.Typeface;
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

    private final ArrayList<Uri> songs = new ArrayList<>();
    private final ArrayList<String> songNames = new ArrayList<>();
    private final Set<String> knownSongs = new HashSet<>();

    private LinearLayout songList;
    private TextView currentSongText;
    private TextView statusText;
    private TextView elapsedText;
    private TextView durationText;
    private SeekBar seekBar;

    private int currentIndex = -1;
    private int currentPosition = 0;
    private int currentDuration = 0;
    private boolean userSeeking = false;
    private boolean receiverRegistered = false;
    private int themeIndex = 0;

    private final int[] backgroundColors = {
            Color.rgb(18, 14, 35),
            Color.rgb(35, 15, 20),
            Color.rgb(12, 24, 42),
            Color.rgb(245, 245, 245),
            Color.rgb(40, 12, 35),
            Color.rgb(10, 35, 25)
    };

    private final int[] textColors = {
            Color.WHITE,
            Color.WHITE,
            Color.WHITE,
            Color.BLACK,
            Color.WHITE,
            Color.WHITE
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
                if (currentDuration > 0) {
                    seekBar.setMax(currentDuration);
                    seekBar.setProgress(
                            Math.min(currentPosition, currentDuration));
                } else {
                    seekBar.setProgress(0);
                }
            }

            if (elapsedText != null) {
                elapsedText.setText(formatTime(currentPosition));
            }

            if (durationText != null) {
                durationText.setText(formatTime(currentDuration));
            }

            if (statusText != null && prepared) {
                statusText.setText(
                        playing ? "▶ Playing" : "Ⅱ Paused");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        registerProgressReceiver();
        createScreen();
    }

    private void registerProgressReceiver() {
        IntentFilter filter = new IntentFilter(
                PlaybackService.ACTION_PROGRESS);

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                    progressReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
            );
        } else {
            registerReceiver(progressReceiver, filter);
        }

        receiverRegistered = true;
    }

    private void createScreen() {
        int bg = backgroundColors[themeIndex];
        int fg = textColors[themeIndex];

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(bg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(22, 24, 22, 28);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        scrollView.addView(root);

        TextView brand = new TextView(this);
        brand.setText("SUNO");
        brand.setTextColor(fg);
        brand.setTextSize(15);
        brand.setLetterSpacing(0.3f);
        brand.setGravity(Gravity.CENTER);
        root.addView(brand, matchWrap());

        TextView title = new TextView(this);
        title.setText("Suno by Bhuvan");
        title.setTextColor(fg);
        title.setTextSize(29);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 8, 0, 4);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("Your music. Your vibe.");
        subtitle.setTextColor(fg);
        subtitle.setAlpha(0.75f);
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, matchWrap());

        currentSongText = new TextView(this);
        currentSongText.setText("Choose a song to get started");
        currentSongText.setTextColor(fg);
        currentSongText.setTextSize(19);
        currentSongText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        currentSongText.setGravity(Gravity.CENTER);
        currentSongText.setPadding(0, 30, 0, 8);
        root.addView(currentSongText, matchWrap());

        statusText = new TextView(this);
        statusText.setText("Ready to play");
        statusText.setTextColor(fg);
        statusText.setGravity(Gravity.CENTER);
        statusText.setTextSize(13);
        statusText.setPadding(0, 0, 0, 18);
        root.addView(statusText, matchWrap());

        seekBar = new SeekBar(this);
        seekBar.setMax(100);
        root.addView(seekBar, matchWrap());

        LinearLayout times = new LinearLayout(this);
        times.setOrientation(LinearLayout.HORIZONTAL);
        times.setGravity(Gravity.CENTER_VERTICAL);

        elapsedText = new TextView(this);
        elapsedText.setText("00:00");
        elapsedText.setTextColor(fg);
        elapsedText.setTextSize(12);

        durationText = new TextView(this);
        durationText.setText("00:00");
        durationText.setTextColor(fg);
        durationText.setTextSize(12);
        durationText.setGravity(Gravity.END);

        times.addView(elapsedText,
                new LinearLayout.LayoutParams(0, -2, 1));

        times.addView(durationText,
                new LinearLayout.LayoutParams(0, -2, 1));

        root.addView(times, matchWrap());

        seekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(
                    SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
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

                if (currentDuration > 0 && currentIndex >= 0) {
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

        addButton(root, "＋  Choose Songs", v -> chooseAudio());
        addButton(root, "📁  Scan Music Folder", v -> chooseFolder());

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setOrientation(LinearLayout.HORIZONTAL);

        Button previous = makeButton("⏮");
        Button play = makeButton("▶  PLAY");
        Button pause = makeButton("Ⅱ  PAUSE");
        Button next = makeButton("⏭");

        previous.setOnClickListener(v -> playPrevious());
        play.setOnClickListener(v -> playCurrent());
        pause.setOnClickListener(v -> pauseCurrent());
        next.setOnClickListener(v -> playNext());

        controls.addView(previous, new LinearLayout.LayoutParams(
                0, 52, 1));
        controls.addView(play, new LinearLayout.LayoutParams(
                0, 52, 2));
        controls.addView(pause, new LinearLayout.LayoutParams(
                0, 52, 2));
        controls.addView(next, new LinearLayout.LayoutParams(
                0, 52, 1));

        root.addView(controls, matchWrap());

        addButton(root, "🎨  Change Theme", v -> {
            themeIndex = (themeIndex + 1) % backgroundColors.length;
            createScreen();
            refreshSongList();
            updateSongDisplay();

            if (currentDuration > 0) {
                seekBar.setMax(currentDuration);
                seekBar.setProgress(currentPosition);
                elapsedText.setText(formatTime(currentPosition));
                durationText.setText(formatTime(currentDuration));
            }
        });

        TextView libraryTitle = new TextView(this);
        libraryTitle.setText("YOUR MUSIC LIBRARY");
        libraryTitle.setTextColor(fg);
        libraryTitle.setTextSize(15);
        libraryTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        libraryTitle.setPadding(0, 28, 0, 12);
        root.addView(libraryTitle, matchWrap());

        songList = new LinearLayout(this);
        songList.setOrientation(LinearLayout.VERTICAL);
        root.addView(songList, matchWrap());

        setContentView(scrollView);

        refreshSongList();
        updateSongDisplay();
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private Button makeButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12);
        return button;
    }

    private void addButton(
            LinearLayout parent,
            String text,
            View.OnClickListener listener) {

        Button button = makeButton(text);
        button.setTextSize(14);
        button.setOnClickListener(listener);

        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 7, 0, 7);
        parent.addView(button, params);
    }

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
                Toast.makeText(this,
                        "Could not open selected songs",
                        Toast.LENGTH_LONG).show();
            }

            refreshSongList();
            updateSongDisplay();

            Toast.makeText(this,
                    (songs.size() - before) + " new song(s) added",
                    Toast.LENGTH_SHORT).show();
        }

        if (requestCode == PICK_FOLDER) {
            Uri treeUri = data.getData();

            if (treeUri == null) {
                return;
            }

            try {
                int flags = data.getFlags()
                        & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                getContentResolver().takePersistableUriPermission(
                        treeUri, flags);

                statusText.setText("Scanning folder...");
                scanFolder(treeUri);

            } catch (Exception e) {
                Toast.makeText(this,
                        "Folder permission failed",
                        Toast.LENGTH_LONG).show();
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

    private void addSong(Uri uri) {
        if (uri == null) {
            return;
        }

        String key = uri.toString();

        if (knownSongs.contains(key)) {
            return;
        }

        String name = getSongName(uri);

        knownSongs.add(key);
        songs.add(uri);
        songNames.add(name);

        if (currentIndex == -1) {
            currentIndex = 0;
        }
    }

    private String getSongName(Uri uri) {
        Cursor cursor = null;

        try {
            cursor = getContentResolver().query(
                    uri,
                    new String[]{OpenableColumns.DISPLAY_NAME},
                    null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME);

                if (column >= 0) {
                    String name = cursor.getString(column);

                    if (name != null && !name.isEmpty()) {
                        return name;
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return "Unknown song";
    }

    private void scanFolder(Uri treeUri) {
        int before = songs.size();

        try {
            String rootId = DocumentsContract.getTreeDocumentId(
                    treeUri);

            scanDocument(treeUri, rootId, 0);

            refreshSongList();
            updateSongDisplay();

            int added = songs.size() - before;

            if (added > 0) {
                statusText.setText("Found " + added + " new song(s)");
            } else {
                statusText.setText("No new audio files found");
            }

            Toast.makeText(this,
                    "Found " + added + " new audio file(s)",
                    Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            statusText.setText("Folder scan failed");
            Toast.makeText(this,
                    "Could not scan this folder. Try another folder.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void scanDocument(
            Uri treeUri, String documentId, int depth) {

        if (depth > 20) {
            return;
        }

        Uri childrenUri =
                DocumentsContract.buildChildDocumentsUriUsingTree(
                        treeUri, documentId);

        String[] columns = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };

        ArrayList<String> directoryIds = new ArrayList<>();

        Cursor cursor = null;

        try {
            cursor = getContentResolver().query(
                    childrenUri, columns, null, null, null);

            if (cursor == null) {
                return;
            }

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

                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    directoryIds.add(childId);
                } else if (isAudioFile(name, mime)) {
                    Uri fileUri =
                            DocumentsContract.buildDocumentUriUsingTree(
                                    treeUri, childId);

                    addSong(fileUri);
                }
            }

        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        for (String directoryId : directoryIds) {
            scanDocument(treeUri, directoryId, depth + 1);
        }
    }

    private boolean isAudioFile(String name, String mime) {
        if (mime != null && mime.startsWith("audio/")) {
            return true;
        }

        if (name == null) {
            return false;
        }

        String lower = name.toLowerCase(Locale.ROOT);

        String[] extensions = {
                ".mp3", ".m4a", ".aac", ".ogg", ".wav",
                ".flac", ".opus", ".wma", ".3gp",
                ".amr", ".mid", ".midi", ".mp4"
        };

        for (String extension : extensions) {
            if (lower.endsWith(extension)) {
                return true;
            }
        }

        return false;
    }

    private void refreshSongList() {
        if (songList == null) {
            return;
        }

        songList.removeAllViews();

        if (songs.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No songs yet. Choose songs or scan a folder.");
            empty.setTextColor(textColors[themeIndex]);
            empty.setTextSize(14);
            empty.setPadding(0, 5, 0, 15);
            songList.addView(empty, matchWrap());
            return;
        }

        for (int i = 0; i < songNames.size(); i++) {
            final int index = i;

            TextView item = new TextView(this);
            item.setText((i + 1) + ".  " + songNames.get(i));
                        item.setTextColor(textColors[themeIndex]);
            item.setTextSize(14);
            item.setPadding(12, 14, 12, 14);

            item.setBackgroundColor(
                    themeIndex == 3
                            ? Color.rgb(225, 225, 225)
                            : Color.rgb(45, 40, 60));

            LinearLayout.LayoutParams params = matchWrap();
            params.setMargins(0, 3, 0, 3);
            songList.addView(item, params);

            item.setOnClickListener(v -> {
                currentIndex = index;
                playCurrent();
            });
        }
    }

    private void playCurrent() {
        if (songs.isEmpty() || currentIndex < 0
                || currentIndex >= songs.size()) {
            Toast.makeText(this,
                    "First choose songs or scan a music folder",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Uri uri = songs.get(currentIndex);

        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(PlaybackService.ACTION_PLAY);
        intent.putExtra(
                PlaybackService.EXTRA_AUDIO_URI, uri.toString());
        intent.putExtra(
                PlaybackService.EXTRA_SONG_NAME,
                songNames.get(currentIndex));

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }

            updateSongDisplay();
            statusText.setText("Loading song...");
            elapsedText.setText("00:00");
            durationText.setText("00:00");
            seekBar.setProgress(0);

        } catch (Exception e) {
            statusText.setText("Could not start playback");
            Toast.makeText(this,
                    "Playback could not start",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void pauseCurrent() {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(PlaybackService.ACTION_PAUSE);
        startService(intent);
    }

    private void playNext() {
        if (songs.isEmpty()) {
            return;
        }

        currentIndex = (currentIndex + 1) % songs.size();
        playCurrent();
    }

    private void playPrevious() {
        if (songs.isEmpty()) {
            return;
        }

        currentIndex--;

        if (currentIndex < 0) {
            currentIndex = songs.size() - 1;
        }

        playCurrent();
    }

    private void updateSongDisplay() {
        if (currentSongText == null) {
            return;
        }

        if (currentIndex >= 0 && currentIndex < songNames.size()) {
            currentSongText.setText(songNames.get(currentIndex));
        } else {
            currentSongText.setText("Choose a song to get started");
        }
    }

    private String formatTime(int milliseconds) {
        int totalSeconds = Math.max(0, milliseconds) / 1000;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;

        return String.format(
                Locale.getDefault(),
                "%02d:%02d",
                minutes,
                seconds);
    }

    @Override
    protected void onDestroy() {
        if (receiverRegistered) {
            try {
                unregisterReceiver(progressReceiver);
            } catch (Exception ignored) {
            }

            receiverRegistered = false;
        }

        super.onDestroy();
    }
                             }
