package com.bhuvan.sunobybhuvan;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bhuvan.sunobybhuvan.PlaybackService.Song;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    private static final int PICK_AUDIO = 101;
    private static final int PICK_FOLDER = 102;
    private static final int REQUEST_NOTIFICATIONS = 201;

    private static final String PREFS = "SunoSettings";
    private static final String THEME_KEY = "theme";
    private static final String FOLDERS_KEY = "folders";
    private static final String SONGS_KEY = "single_songs";
    private static final String ASKED_NOTIFICATIONS_KEY = "asked_notifications";

    private static final String[] THEME_NAMES = {
            "Midnight Black", "Royal Purple", "Ocean Blue",
            "Rose Pink", "Emerald Green", "Crimson Night"
    };

    private static final int[] THEME_BACKGROUND = {
            Color.rgb(12, 13, 19), Color.rgb(24, 14, 39),
            Color.rgb(8, 22, 38), Color.rgb(40, 17, 29),
            Color.rgb(10, 32, 26), Color.rgb(12, 5, 7)
    };

    private static final int[] THEME_PANEL = {
            Color.rgb(34, 35, 46), Color.rgb(55, 37, 78),
            Color.rgb(23, 48, 72), Color.rgb(72, 35, 53),
            Color.rgb(24, 59, 47), Color.rgb(38, 12, 16)
    };

    private static final int[] THEME_ACCENT = {
            Color.rgb(153, 117, 255), Color.rgb(181, 117, 255),
            Color.rgb(45, 190, 255), Color.rgb(255, 105, 155),
            Color.rgb(56, 220, 145), Color.rgb(225, 29, 46)
    };

    private static final int[] THEME_SECONDARY = {
            Color.LTGRAY, Color.rgb(211, 194, 230),
            Color.rgb(185, 216, 238), Color.rgb(240, 195, 215),
            Color.rgb(180, 225, 205), Color.rgb(222, 170, 175)
    };

    private int theme = 0;
    private int backgroundColor;
    private int panelColor;
    private int accentColor;
    private int secondaryColor;
    private final int textColor = Color.WHITE;

    private final ArrayList<Uri> folders = new ArrayList<>();
    private final ArrayList<Uri> singleSongs = new ArrayList<>();

    private volatile boolean scanning = false;
    private volatile int scanGeneration = 0;
    private boolean rescanPending = false;

    private int currentIndex = -1;
    private int position = 0;
    private int duration = 0;
    private boolean playing = false;
    private boolean prepared = false;
    private boolean seeking = false;
    private boolean receiverRegistered = false;
    private int currentPage = 0;

    private LinearLayout root;
    private final View[] pages = new View[3];
    private final Button[] tabButtons = new Button[3];
    private ListView listView;
    private SongAdapter adapter;
    private TextView libraryHeader;
    private TextView emptyText;
    private TextView miniTitle;
    private TextView nowTitle;
    private TextView nowArtist;
    private TextView nowStatus;
    private TextView currentTimeText;
    private TextView totalTimeText;
    private Button miniPlayButton;
    private Button nowPlayButton;
    private LinearLayout miniBar;
    private LinearLayout foldersContainer;
    private SeekBar seekBar;
    private Button shuffleButton;
    private Button loopButton;
    private Button sleepButton;
    private TextView loopInfoText;

    private static final int EQ_BARS = 5;
    private final ObjectAnimator[] eqAnimators = new ObjectAnimator[EQ_BARS];

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable sleepTicker = new Runnable() {
        @Override
        public void run() {
            updateSleepUi();
            uiHandler.postDelayed(this, 1000);
        }
    };

    private static class MetaResult {
        final Song song;
        final String title;
        final String artist;
        final long durationMs;

        MetaResult(Song song, String title, String artist, long durationMs) {
            this.song = song;
            this.title = title;
            this.artist = artist;
            this.durationMs = durationMs;
        }
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;

            if (PlaybackService.ACTION_ERROR.equals(intent.getAction())) {
                String message =
                        intent.getStringExtra(PlaybackService.EXTRA_MESSAGE);
                if (message != null) {
                    Toast.makeText(MainActivity.this, message,
                            Toast.LENGTH_LONG).show();
                }
                return;
            }

            if (!PlaybackService.ACTION_PROGRESS.equals(intent.getAction())) {
                return;
            }

            position = intent.getIntExtra(PlaybackService.EXTRA_POSITION, 0);
            duration = intent.getIntExtra(PlaybackService.EXTRA_DURATION, 0);
            playing = intent.getBooleanExtra(PlaybackService.EXTRA_PLAYING, false);
            prepared = intent.getBooleanExtra(PlaybackService.EXTRA_PREPARED, false);

            int index = intent.getIntExtra(PlaybackService.EXTRA_INDEX, -1);
            boolean changed = index != currentIndex;
            currentIndex = index;

            if (changed && adapter != null) {
                adapter.notifyDataSetChanged();
            }

            updatePlayerUi();
        }
    };

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        theme = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(THEME_KEY, 0);
        if (theme < 0 || theme >= THEME_NAMES.length) theme = 0;
        applyTheme();

        PlaybackService.loadSettings(this);

        loadUris(FOLDERS_KEY, folders);
        loadUris(SONGS_KEY, singleSongs);

        buildUi();
        registerReceivers();
        syncCurrentIndex();
        refreshAllUi();

        if (PlaybackService.library.isEmpty()
                && (!folders.isEmpty() || !singleSongs.isEmpty())) {
            rescanLibrary(false);
        }

        askNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();

        uiHandler.removeCallbacks(sleepTicker);
        uiHandler.post(sleepTicker);

        syncCurrentIndex();

        if (PlaybackService.currentSong != null) {
            sendCommand(PlaybackService.ACTION_REFRESH);
        } else {
            playing = false;
            prepared = false;
            position = 0;
            duration = 0;
            updatePlayerUi();
        }
    }

    @Override
    protected void onPause() {
        uiHandler.removeCallbacks(sleepTicker);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        scanGeneration++;
        uiHandler.removeCallbacks(sleepTicker);
        cancelEqualizer();

        if (receiverRegistered) {
            try {
                unregisterReceiver(receiver);
            } catch (Exception ignored) {
            }
            receiverRegistered = false;
        }

        super.onDestroy();
    }

    private void registerReceivers() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(PlaybackService.ACTION_PROGRESS);
        filter.addAction(PlaybackService.ACTION_ERROR);

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }

        receiverRegistered = true;
    }

    // ------------------------------------------------------------------ theme

    private void applyTheme() {
        backgroundColor = THEME_BACKGROUND[theme];
        panelColor = THEME_PANEL[theme];
        accentColor = THEME_ACCENT[theme];
        secondaryColor = THEME_SECONDARY[theme];
    }

    private void selectTheme(int index) {
        theme = index;

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putInt(THEME_KEY, theme)
                .apply();

        applyTheme();
        buildUi();
        refreshAllUi();
    }

    // --------------------------------------------------------------- helpers

    private int dp(int value) {
        return (int) (value
                * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private Button makeButton(String text, int color) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(Color.WHITE);
        button.setBackground(rounded(color, 16));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        return button;
    }

    private void addButton(LinearLayout parent, String text, int color,
                           View.OnClickListener listener, int topMarginDp) {
        Button button = makeButton(text, color);
        button.setOnClickListener(listener);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, dp(52));
        params.topMargin = dp(topMarginDp);
        parent.addView(button, params);
    }

    private String formatTime(long milliseconds) {
        long totalSeconds = Math.max(0, milliseconds) / 1000;
        return String.format(Locale.getDefault(), "%02d:%02d",
                totalSeconds / 60, totalSeconds % 60);
    }

    private Song songAt(int index) {
        if (index >= 0 && index < PlaybackService.library.size()) {
            return PlaybackService.library.get(index);
        }
        return null;
    }

    private String displayArtist(Song song) {
        if (song.artist == null || song.artist.trim().isEmpty()) {
            return "Unknown artist";
        }
        return song.artist;
    }

    private void syncCurrentIndex() {
        Song current = PlaybackService.currentSong;
        currentIndex = current == null
                ? -1 : PlaybackService.library.indexOf(current);
    }

    // --------------------------------------------------------------------- UI

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(backgroundColor);
        root.setFitsSystemWindows(true);

        TextView brand = label("♫  SUNO BY BHUVAN", 15, accentColor, true);
        brand.setPadding(dp(20), dp(14), dp(20), dp(8));
        root.addView(brand, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout content = new FrameLayout(this);
        pages[0] = buildLibraryPage();
        pages[1] = buildNowPlayingPage();
        pages[2] = buildFoldersPage();

        for (View page : pages) {
            content.addView(page, new FrameLayout.LayoutParams(-1, -1));
        }

        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        miniBar = buildMiniBar();
        LinearLayout.LayoutParams miniParams =
                new LinearLayout.LayoutParams(-1, -2);
        miniParams.setMargins(dp(12), dp(4), dp(12), dp(6));
        root.addView(miniBar, miniParams);

        LinearLayout.LayoutParams tabParams =
                new LinearLayout.LayoutParams(-1, -2);
        tabParams.setMargins(dp(12), 0, dp(12), dp(10));
        root.addView(buildTabBar(), tabParams);

        setContentView(root);
        showPage(currentPage);
    }

    private View buildLibraryPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        libraryHeader = label("", 13, secondaryColor, true);
        libraryHeader.setPadding(dp(20), dp(4), dp(20), dp(8));
        page.addView(libraryHeader, new LinearLayout.LayoutParams(-1, -2));

        emptyText = label(
                "Your library is empty.\n\nOpen the Folders tab and add a music folder, "
                        + "or choose some songs.",
                14, secondaryColor, false);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setPadding(dp(30), dp(30), dp(30), dp(30));
        page.addView(emptyText, new LinearLayout.LayoutParams(-1, -2));

        listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setFastScrollEnabled(true);
        listView.setPadding(dp(8), 0, dp(8), 0);
        listView.setClipToPadding(false);

        adapter = new SongAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(
                (parent, view, itemPosition, id) -> sendPlay(itemPosition));

        page.addView(listView, new LinearLayout.LayoutParams(-1, 0, 1));
        return page;
    }

    private View buildNowPlayingPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(10), dp(24), dp(20));
        box.setGravity(Gravity.CENTER_HORIZONTAL);

        FrameLayout art = new FrameLayout(this);
        art.setBackground(rounded(accentColor, 28));

        LinearLayout eq = new LinearLayout(this);
        eq.setOrientation(LinearLayout.HORIZONTAL);
        eq.setGravity(Gravity.BOTTOM);
        cancelEqualizer();

        for (int i = 0; i < EQ_BARS; i++) {
            View eqBar = new View(this);
            eqBar.setBackground(rounded(Color.WHITE, 4));
            eqBar.setPivotY(dp(80));
            eqBar.setScaleY(0.25f);

            LinearLayout.LayoutParams eqParams =
                    new LinearLayout.LayoutParams(dp(12), dp(80));
            eqParams.setMargins(dp(5), 0, dp(5), 0);
            eq.addView(eqBar, eqParams);

            ObjectAnimator animator =
                    ObjectAnimator.ofFloat(eqBar, "scaleY", 0.25f, 1f);
            animator.setDuration(450 + i * 130);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setRepeatMode(ValueAnimator.REVERSE);
            eqAnimators[i] = animator;
        }

        art.addView(eq, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        box.addView(art, new LinearLayout.LayoutParams(-1, dp(190)));

        nowTitle = label("No song selected", 22, textColor, true);
        nowTitle.setGravity(Gravity.CENTER);
        nowTitle.setMaxLines(2);
        nowTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(24);
        box.addView(nowTitle, titleParams);

        nowArtist = label("", 15, secondaryColor, false);
        nowArtist.setGravity(Gravity.CENTER);
        nowArtist.setSingleLine(true);
        nowArtist.setEllipsize(TextUtils.TruncateAt.END);
        box.addView(nowArtist, new LinearLayout.LayoutParams(-1, -2));

        nowStatus = label("", 13, accentColor, false);
        nowStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams =
                new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(6);
        box.addView(nowStatus, statusParams);

        seekBar = new SeekBar(this);
        seekBar.setMax(1);
            seekBar.setProgressTintList(ColorStateList.valueOf(accentColor));
        seekBar.setThumbTintList(ColorStateList.valueOf(accentColor));
        LinearLayout.LayoutParams seekParams =
                new LinearLayout.LayoutParams(-1, -2);
        seekParams.topMargin = dp(20);
        box.addView(seekBar, seekParams);

        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);

        currentTimeText = label("00:00", 12, secondaryColor, false);
        totalTimeText = label("00:00", 12, secondaryColor, false);
        totalTimeText.setGravity(Gravity.END);

        timeRow.addView(currentTimeText, new LinearLayout.LayoutParams(0, -2, 1));
        timeRow.addView(totalTimeText, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(timeRow, new LinearLayout.LayoutParams(-1, -2));

        seekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            SeekBar bar, int progress, boolean fromUser) {
                        if (fromUser) {
                            currentTimeText.setText(formatTime(progress));
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar bar) {
                        seeking = true;
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar bar) {
                        seeking = false;

                        if (prepared && PlaybackService.currentSong != null) {
                            Intent intent = new Intent(
                                    MainActivity.this, PlaybackService.class);
                            intent.setAction(PlaybackService.ACTION_SEEK);
                            intent.putExtra(
                                    PlaybackService.EXTRA_SEEK_POSITION,
                                    bar.getProgress());
                            try {
                                startService(intent);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                });

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);

        shuffleButton = makeButton("🔀", panelColor);
        Button previous = makeButton("⏮", panelColor);
        nowPlayButton = makeButton("▶", accentColor);
        Button next = makeButton("⏭", panelColor);
        loopButton = makeButton("🔁", panelColor);

        shuffleButton.setTextSize(19);
        previous.setTextSize(20);
        nowPlayButton.setTextSize(24);
        next.setTextSize(20);
        loopButton.setTextSize(19);

        shuffleButton.setOnClickListener(v -> {
            pop(v);
            toggleShuffle();
        });
        previous.setOnClickListener(v -> {
            pop(v);
            playPrevious();
        });
        nowPlayButton.setOnClickListener(v -> {
            pop(v);
            togglePlayback();
        });
        next.setOnClickListener(v -> {
            pop(v);
            playNext();
        });
        loopButton.setOnClickListener(v -> {
            pop(v);
            cycleLoop();
        });

        LinearLayout.LayoutParams sideParams =
                new LinearLayout.LayoutParams(0, dp(60), 1);
        sideParams.setMargins(dp(3), 0, dp(3), 0);
        LinearLayout.LayoutParams centerParams =
                new LinearLayout.LayoutParams(0, dp(60), 1.3f);
        centerParams.setMargins(dp(3), 0, dp(3), 0);

        controls.addView(shuffleButton, sideParams);
        controls.addView(previous, sideParams);
        controls.addView(nowPlayButton, centerParams);
        controls.addView(next, sideParams);
        controls.addView(loopButton, sideParams);

        LinearLayout.LayoutParams controlsParams =
                new LinearLayout.LayoutParams(-1, -2);
        controlsParams.topMargin = dp(22);
        box.addView(controls, controlsParams);

        loopInfoText = label("", 12, secondaryColor, false);
        loopInfoText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams infoParams =
                new LinearLayout.LayoutParams(-1, -2);
        infoParams.topMargin = dp(8);
        box.addView(loopInfoText, infoParams);

        sleepButton = makeButton("😴  Sleep timer: off", panelColor);
        sleepButton.setTextSize(14);
        sleepButton.setOnClickListener(v -> {
            pop(v);
            showSleepDialog();
        });
        LinearLayout.LayoutParams sleepParams =
                new LinearLayout.LayoutParams(-1, dp(48));
        sleepParams.topMargin = dp(12);
        box.addView(sleepButton, sleepParams);

        scroll.addView(box);
        return scroll;
    }

    private View buildFoldersPage() {
        ScrollView scroll = new ScrollView(this);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(20));

        addButton(box, "📁  Add Music Folder", accentColor,
                v -> chooseFolder(), 0);
        addButton(box, "＋  Choose Songs", panelColor,
                v -> chooseAudio(), 10);
        addButton(box, "🔄  Rescan Library", panelColor,
                v -> rescanLibrary(true), 10);

        TextView note = label(
                "Add as many folders as you like, one after another. "
                        + "Subfolders are scanned too and duplicates are skipped. "
                        + "Android does not allow choosing the very top of Internal "
                        + "storage or the Download folder, so pick a folder such as "
                        + "Music (or a folder inside it).",
                12, secondaryColor, false);
        LinearLayout.LayoutParams noteParams =
                new LinearLayout.LayoutParams(-1, -2);
        noteParams.topMargin = dp(12);
        box.addView(note, noteParams);

        TextView foldersLabel = label("SELECTED FOLDERS", 13, secondaryColor, true);
        LinearLayout.LayoutParams foldersLabelParams =
                new LinearLayout.LayoutParams(-1, -2);
        foldersLabelParams.topMargin = dp(22);
        box.addView(foldersLabel, foldersLabelParams);

        foldersContainer = new LinearLayout(this);
        foldersContainer.setOrientation(LinearLayout.VERTICAL);
        box.addView(foldersContainer, new LinearLayout.LayoutParams(-1, -2));

        TextView themeLabel = label("THEME", 13, secondaryColor, true);
        LinearLayout.LayoutParams themeLabelParams =
                new LinearLayout.LayoutParams(-1, -2);
        themeLabelParams.topMargin = dp(26);
        box.addView(themeLabel, themeLabelParams);

        for (int i = 0; i < THEME_NAMES.length; i++) {
            final int index = i;
            String text = THEME_NAMES[i] + (i == theme ? "   ✓" : "");
            addButton(box, text, THEME_ACCENT[i], v -> selectTheme(index), 8);
        }

        scroll.addView(box);
        return scroll;
    }

    private LinearLayout buildMiniBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(rounded(panelColor, 16));
        bar.setPadding(dp(14), dp(4), dp(8), dp(4));

        miniTitle = label("Nothing playing", 14, textColor, true);
        miniTitle.setSingleLine(true);
        miniTitle.setEllipsize(TextUtils.TruncateAt.END);
        miniTitle.setGravity(Gravity.CENTER_VERTICAL);
        miniTitle.setOnClickListener(v -> showPage(1));
        bar.addView(miniTitle, new LinearLayout.LayoutParams(0, dp(48), 1));

        miniPlayButton = makeButton("▶", accentColor);
        miniPlayButton.setOnClickListener(v -> togglePlayback());
        bar.addView(miniPlayButton, new LinearLayout.LayoutParams(dp(52), dp(44)));

        Button next = makeButton("⏭", backgroundColor);
        next.setOnClickListener(v -> playNext());
        LinearLayout.LayoutParams nextParams =
                new LinearLayout.LayoutParams(dp(52), dp(44));
        nextParams.leftMargin = dp(6);
        bar.addView(next, nextParams);

        return bar;
    }

    private LinearLayout buildTabBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);

        String[] titles = {"🎵 Library", "▶ Now Playing", "📁 Folders"};

        for (int i = 0; i < titles.length; i++) {
            final int page = i;
            Button button = makeButton(titles[i], panelColor);
            button.setTextSize(13);
            button.setOnClickListener(v -> showPage(page));
            tabButtons[i] = button;

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(0, dp(48), 1);
            params.setMargins(dp(3), 0, dp(3), 0);
            bar.addView(button, params);
        }

        return bar;
    }

    private void showPage(int page) {
        currentPage = page;

        for (int i = 0; i < pages.length; i++) {
            if (pages[i] != null) {
                pages[i].setVisibility(i == page ? View.VISIBLE : View.GONE);
            }
            if (tabButtons[i] != null) {
                tabButtons[i].setBackground(
                        rounded(i == page ? accentColor : panelColor, 14));
            }
        }

        updateMiniBarVisibility();
    }

    private void updateMiniBarVisibility() {
        if (miniBar == null) return;

        boolean show = songAt(currentIndex) != null && currentPage != 1;
        miniBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void refreshAllUi() {
        updateLibraryHeader();
        refreshFoldersUi();
        updatePlayerUi();

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void updateLibraryHeader() {
        if (libraryHeader == null) return;

        int count = PlaybackService.library.size();

        if (scanning) {
            libraryHeader.setText("SCANNING YOUR MUSIC...  •  " + count + " found");
        } else {
            libraryHeader.setText("YOUR LIBRARY  •  " + count
                    + (count == 1 ? " song" : " songs"));
        }

        emptyText.setVisibility(count == 0 && !scanning
                ? View.VISIBLE : View.GONE);
    }

    private void updatePlayerUi() {
        if (nowTitle == null) return;

        Song song = songAt(currentIndex);

        nowTitle.setText(song == null ? "No song selected" : song.title);
        nowArtist.setText(song == null ? "" : displayArtist(song));
        miniTitle.setText(song == null ? "Nothing playing" : song.title);

        String state;
        if (song == null) {
            state = PlaybackService.library.isEmpty()
                    ? "Add a music folder to begin."
                    : "Tap a song in your library to play it.";
        } else if (!prepared) {
            state = "Loading...";
        } else {
            state = playing ? "♫ Now playing" : "Ⅱ Paused";
        }
        nowStatus.setText(state);

        String toggle = playing ? "Ⅱ" : "▶";
        nowPlayButton.setText(toggle);
        miniPlayButton.setText(toggle);

        if (prepared && song != null) {
            if (!seeking) {
                seekBar.setMax(Math.max(duration, 1));
                seekBar.setProgress(Math.min(position, Math.max(duration, 1)));
                currentTimeText.setText(formatTime(position));
            }
            totalTimeText.setText(formatTime(duration));
        } else if (!seeking) {
            seekBar.setMax(1);
            seekBar.setProgress(0);
            currentTimeText.setText("00:00");
            totalTimeText.setText("00:00");
        }

        updateModeUi();
        updateSleepUi();
        updateEqualizer(playing && prepared && song != null);
        updateMiniBarVisibility();
    }

    private void refreshFoldersUi() {
        if (foldersContainer == null) return;

        foldersContainer.removeAllViews();

        if (folders.isEmpty() && singleSongs.isEmpty()) {
            foldersContainer.addView(
                    label("No folders added yet.", 14, secondaryColor, false),
                    new LinearLayout.LayoutParams(-1, -2));
            return;
        }

        for (final Uri folder : folders) {
            Button remove = makeButton("Remove", backgroundColor);
            remove.setTextSize(12);
            remove.setOnClickListener(v -> removeFolder(folder));
            addRow(folderDisplayName(folder), remove);
        }

        if (!singleSongs.isEmpty()) {
            Button clear = makeButton("Clear", backgroundColor);
            clear.setTextSize(12);
            clear.setOnClickListener(v -> clearSingleSongs());
            addRow(singleSongs.size() + " song(s) chosen individually", clear);
        }
    }

    private void addRow(String text, Button button) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(rounded(panelColor, 14));
        row.setPadding(dp(14), dp(6), dp(8), dp(6));

        row.addView(label(text, 14, textColor, false),
                new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(button, new LinearLayout.LayoutParams(dp(84), dp(42)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(8);
        foldersContainer.addView(row, params);
    }

    private class SongAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return PlaybackService.library.size();
        }

        @Override
        public Object getItem(int index) {
            return PlaybackService.library.get(index);
        }

        @Override
        public long getItemId(int index) {
            return index;
        }

        @Override
        public View getView(int index, View convertView, ViewGroup parent) {
            LinearLayout row;
            TextView title;
            TextView sub;

            if (convertView == null) {
                row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(14), dp(10), dp(14), dp(10));

                title = new TextView(MainActivity.this);
                title.setTextSize(15);
                title.setTypeface(null, Typeface.BOLD);
                title.setSingleLine(true);
                title.setEllipsize(TextUtils.TruncateAt.END);

                sub = new TextView(MainActivity.this);
                sub.setTextSize(12);
                sub.setSingleLine(true);
                sub.setEllipsize(TextUtils.TruncateAt.END);

                row.addView(title);
                row.addView(sub);
                row.setTag(new TextView[]{title, sub});
            } else {
                row = (LinearLayout) convertView;
                TextView[] views = (TextView[]) row.getTag();
                title = views[0];
                sub = views[1];
            }

            Song song = PlaybackService.library.get(index);
            boolean current = index == currentIndex;

            title.setText(song.title);
            title.setTextColor(current ? accentColor : textColor);

            String details = displayArtist(song);
            if (song.durationMs > 0) {
                details += "  •  " + formatTime(song.durationMs);
            }
            sub.setText(details);
            sub.setTextColor(secondaryColor);

            row.setBackground(rounded(
                    current ? panelColor : Color.TRANSPARENT, 12));

            return row;
        }
    }

    // --------------------------------------------------------------- playback

    private void sendPlay(int index) {
        if (index < 0 || index >= PlaybackService.library.size()) {
            Toast.makeText(this,
                    "Add a music folder or choose songs first.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(PlaybackService.ACTION_PLAY);
        intent.putExtra(PlaybackService.EXTRA_INDEX, index);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Exception e) {
            Toast.makeText(this,
                    "Playback could not start. Please try again.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void sendCommand(String action) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(action);

        try {
            startService(intent);
        } catch (Exception ignored) {
        }
    }

    private void togglePlayback() {
        if (PlaybackService.library.isEmpty()) {
            Toast.makeText(this,
                    "Add a music folder or choose songs first.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (playing) {
            sendCommand(PlaybackService.ACTION_PAUSE);
                                         } else {
            sendPlay(currentIndex >= 0 ? currentIndex : 0);
        }
    }

    private void playNext() {
        if (PlaybackService.library.isEmpty()) return;

        if (PlaybackService.currentSong != null) {
            sendCommand(PlaybackService.ACTION_NEXT);
        } else {
            sendPlay(0);
        }
    }

    private void playPrevious() {
        if (PlaybackService.library.isEmpty()) return;

        if (PlaybackService.currentSong != null) {
            sendCommand(PlaybackService.ACTION_PREVIOUS);
        } else {
            sendPlay(0);
        }
    }

    // ------------------------------------------------- shuffle / loop / sleep

    private void pop(View view) {
        view.animate().cancel();
        view.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(70)
                .withEndAction(() -> view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(120)
                        .start())
                .start();
    }

    private void toggleShuffle() {
        PlaybackService.shuffleOn = !PlaybackService.shuffleOn;
        PlaybackService.saveSettings(this);
        updateModeUi();
    }

    private void cycleLoop() {
        PlaybackService.loopMode = (PlaybackService.loopMode + 1) % 3;
        PlaybackService.saveSettings(this);
        updateModeUi();
    }

    private void updateModeUi() {
        if (shuffleButton == null || loopButton == null
                || loopInfoText == null) {
            return;
        }

        boolean shuffle = PlaybackService.shuffleOn;
        int mode = PlaybackService.loopMode;

        shuffleButton.setBackground(
                rounded(shuffle ? accentColor : panelColor, 16));

        loopButton.setText(mode == PlaybackService.LOOP_ONE ? "🔂" : "🔁");
        loopButton.setBackground(rounded(
                mode != PlaybackService.LOOP_OFF ? accentColor : panelColor,
                16));

        String info = "";
        if (mode == PlaybackService.LOOP_ALL) {
            info = "Loop: poori playlist";
        } else if (mode == PlaybackService.LOOP_ONE) {
            info = "Loop: sirf ye song";
        }
        if (shuffle) {
            info = info.isEmpty() ? "Shuffle on" : info + "  •  Shuffle on";
        }
        loopInfoText.setText(info);
    }

    private void updateSleepUi() {
        if (sleepButton == null) return;

        long end = PlaybackService.sleepEndAt;
        long left = end - SystemClock.elapsedRealtime();

        if (end > 0 && left > 0) {
            long secs = (left + 999) / 1000;
            String time;
            if (secs >= 3600) {
                time = String.format(Locale.getDefault(), "%d:%02d:%02d",
                        secs / 3600, (secs % 3600) / 60, secs % 60);
            } else {
                time = String.format(Locale.getDefault(), "%d:%02d",
                        secs / 60, secs % 60);
            }
            sleepButton.setText("😴  Sleep in " + time);
            sleepButton.setBackground(rounded(accentColor, 16));
        } else {
            sleepButton.setText("😴  Sleep timer: off");
            sleepButton.setBackground(rounded(panelColor, 16));
        }
    }

    private void showSleepDialog() {
        if (PlaybackService.currentSong == null) {
            Toast.makeText(this,
                    "Pehle koi gaana chalao, phir sleep timer lagao.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setHint("Minutes, jaise 20");

        FrameLayout holder = new FrameLayout(this);
        holder.setPadding(dp(20), dp(8), dp(20), 0);
        holder.addView(input, new FrameLayout.LayoutParams(-1, -2));

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Sleep timer")
                .setMessage("Kitne minute baad music band ho?")
                .setView(holder)
                .setPositiveButton("Set", (dialog, which) -> {
                    int minutes = 0;
                    try {
                        minutes = Integer.parseInt(
                                input.getText().toString().trim());
                    } catch (NumberFormatException ignored) {
                    }

                    if (minutes < 1 || minutes > 720) {
                        Toast.makeText(MainActivity.this,
                                "1 se 720 ke beech minutes likho.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    setSleepTimer(minutes);
                })
                .setNegativeButton("Close", null);

        if (PlaybackService.sleepEndAt > 0) {
            builder.setNeutralButton("Timer band karo",
                    (dialog, which) -> setSleepTimer(0));
        }

        builder.show();
    }

    private void setSleepTimer(int minutes) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(PlaybackService.ACTION_SLEEP);
        intent.putExtra(PlaybackService.EXTRA_MINUTES, minutes);

        try {
            startService(intent);
        } catch (Exception ignored) {
        }

        PlaybackService.sleepEndAt = minutes > 0
                ? SystemClock.elapsedRealtime() + minutes * 60000L : 0;
        updateSleepUi();

        Toast.makeText(this,
                minutes > 0
                        ? minutes + " minute baad music band hoga."
                        : "Sleep timer band kar diya.",
                Toast.LENGTH_SHORT).show();
    }

    private void updateEqualizer(boolean active) {
        for (ObjectAnimator animator : eqAnimators) {
            if (animator == null) continue;

            if (active) {
                if (!animator.isStarted()) {
                    animator.start();
                } else if (animator.isPaused()) {
                    animator.resume();
                }
            } else if (animator.isStarted() && !animator.isPaused()) {
                animator.pause();
            }
        }
    }

    private void cancelEqualizer() {
        for (int i = 0; i < eqAnimators.length; i++) {
            if (eqAnimators[i] != null) {
                eqAnimators[i].cancel();
                eqAnimators[i] = null;
            }
        }
    }

    // -------------------------------------------------------------- picking

    private void chooseAudio() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        try {
            startActivityForResult(intent, PICK_AUDIO);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this,
                    "No file picker was found on this phone.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void chooseFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        try {
            startActivityForResult(intent, PICK_FOLDER);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this,
                    "No folder picker was found on this phone.",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == PICK_AUDIO) {
            ArrayList<Uri> picked = new ArrayList<>();

            ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount(); i++) {
                    picked.add(clip.getItemAt(i).getUri());
                }
            } else if (data.getData() != null) {
                picked.add(data.getData());
            }

            boolean added = false;

            for (Uri uri : picked) {
                if (uri == null || singleSongs.contains(uri)) continue;

                try {
                    getContentResolver().takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {
                }

                singleSongs.add(uri);
                added = true;
            }

            if (added) {
                saveUris(SONGS_KEY, singleSongs);
                refreshFoldersUi();
                rescanLibrary(true);
            }

        } else if (requestCode == PICK_FOLDER) {
            Uri folder = data.getData();

            if (folder == null) {
                Toast.makeText(this, "No folder was selected.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            if (folders.contains(folder)) {
                Toast.makeText(this, "This folder is already added.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                getContentResolver().takePersistableUriPermission(
                        folder, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception e) {
                Toast.makeText(this,
                        "Android did not keep access to this folder, so you may "
                                + "need to add it again after restarting the app.",
                        Toast.LENGTH_LONG).show();
            }

            folders.add(folder);
            saveUris(FOLDERS_KEY, folders);
            refreshFoldersUi();
            rescanLibrary(true);
        }
    }

    private void removeFolder(Uri folder) {
        folders.remove(folder);

        try {
            getContentResolver().releasePersistableUriPermission(
                    folder, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }

        saveUris(FOLDERS_KEY, folders);
        refreshFoldersUi();
        rescanLibrary(false);
    }

    private void clearSingleSongs() {
        singleSongs.clear();
        saveUris(SONGS_KEY, singleSongs);
        refreshFoldersUi();
        rescanLibrary(false);
    }

    private String folderDisplayName(Uri uri) {
        try {
            String id = DocumentsContract.getTreeDocumentId(uri);
            int colon = id.indexOf(':');
            String volume = colon >= 0 ? id.substring(0, colon) : id;
            String path = colon >= 0 ? id.substring(colon + 1) : "";
            String volumeName =
                    "primary".equals(volume) ? "Internal storage" : "SD card";

            return path.isEmpty() ? volumeName : volumeName + " / " + path;
        } catch (Exception e) {
            return String.valueOf(uri.getLastPathSegment());
        }
    }

    // ------------------------------------------------------------ persistence

    private void saveUris(String key, ArrayList<Uri> uris) {
        Set<String> values = new HashSet<>();

        for (Uri uri : uris) {
            values.add(uri.toString());
        }

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putStringSet(key, values)
                .apply();
    }

    private void loadUris(String key, ArrayList<Uri> target) {
        target.clear();

        Set<String> saved = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getStringSet(key, null);

        if (saved == null) return;

        ArrayList<String> sorted = new ArrayList<>(saved);
        Collections.sort(sorted);

        for (String value : sorted) {
            try {
                target.add(Uri.parse(value));
            } catch (Exception ignored) {
            }
        }
    }

    // --------------------------------------------------------------- scanning

    private void rescanLibrary(final boolean showResult) {
        if (scanning) {
            rescanPending = true;
            return;
        }

        scanning = true;
        scanGeneration++;
        final int generation = scanGeneration;
        updateLibraryHeader();

        final ArrayList<Uri> folderList = new ArrayList<>(folders);
        final ArrayList<Uri> songList = new ArrayList<>(singleSongs);
        final HashMap<String, Song> previous = new HashMap<>();

        for (Song song : PlaybackService.library) {
            previous.put(song.key, song);
        }

        new Thread(() -> {
            ArrayList<Song> found = new ArrayList<>();
            HashSet<String> seen = new HashSet<>();
            int unreadable = 0;

            for (Uri folder : folderList) {
                try {
                    String rootId = DocumentsContract.getTreeDocumentId(folder);
                    scanDocument(folder, rootId, 0, found, seen, previous);
                } catch (Exception e) {
                    unreadable++;
                }
            }

            for (Uri uri : songList) {
                try {
                    addSingleSong(uri, found, seen, previous);
                } catch (Exception ignored) {
                }
            }

            Collections.sort(found, (a, b) -> a.title.compareToIgnoreCase(b.title));

            final ArrayList<Song> result = found;
            final int problems = unreadable;

            runOnUiThread(() ->
                    finishScan(result, problems, generation, showResult));
        }).start();
    }

    private void finishScan(ArrayList<Song> found, int unreadable,
                            int generation, boolean showResult) {
        scanning = false;

        if (isFinishing() || isDestroyed()) return;

        PlaybackService.library.clear();
        PlaybackService.library.addAll(found);

        syncCurrentIndex();
        adapter.notifyDataSetChanged();
        updateLibraryHeader();
        updatePlayerUi();

        if (unreadable > 0) {
            Toast.makeText(this,
                    "Could not read " + unreadable + " folder(s). Remove them "
                            + "and add them again in the Folders tab.",
                    Toast.LENGTH_LONG).show();
        } else if (showResult) {
            Toast.makeText(this,
                    found.size() + " song(s) in your library",
                    Toast.LENGTH_SHORT).show();
        }

        if (rescanPending) {
            rescanPending = false;
            rescanLibrary(showResult);
            return;
        }

        loadMetadata(found, generation);
    }

    private void scanDocument(Uri treeUri, String documentId, int depth,
                              ArrayList<Song> found, HashSet<String> seen,
                              HashMap<String, Song> previous) {
        if (depth > 40) return;

        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri, documentId);

        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
        };

        ArrayList<String> subFolders = new ArrayList<>();
        Cursor cursor = null;

        try {
            cursor = getContentResolver().query(
                    childrenUri, projection, null, null, null);

            if (cursor == null) {
                if (depth == 0) {
                    throw new IllegalStateException("No access to folder");
                }
                return;
            }

            while (cursor.moveToNext()) {
                String childId = cursor.getString(0);
                String name = cursor.getString(1);
                String mime = cursor.getString(2);
                long size = cursor.isNull(3) ? 0 : cursor.getLong(3);

                if (childId == null) continue;

                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    subFolders.add(childId);
                } else if (isAudioFile(name, mime)) {
                    Uri fileUri = DocumentsContract.buildDocumentUriUsingTree(
                            treeUri, childId);
                    addFoundSong(fileUri, childId, name, size,
                            found, seen, previous);
                }
            }
        } catch (RuntimeException e) {
            if (depth == 0) throw e;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        for (String subFolder : subFolders) {
            scanDocument(treeUri, subFolder, depth + 1, found, seen, previous);
        }
    }

    private void addSingleSong(Uri uri, ArrayList<Song> found,
                               HashSet<String> seen,
                               HashMap<String, Song> previous) {
        String documentId;

        try {
            documentId = DocumentsContract.getDocumentId(uri);
        } catch (Exception e) {
                    documentId = uri.toString();
        }

        String name = null;
        long size = 0;
        Cursor cursor = null;

        try {
            cursor = getContentResolver().query(
                    uri,
                    new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                    null, null, null);

            if (cursor == null || !cursor.moveToFirst()) {
                return;
            }

            name = cursor.getString(0);
            size = cursor.isNull(1) ? 0 : cursor.getLong(1);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        addFoundSong(uri, documentId, name, size, found, seen, previous);
    }

    private void addFoundSong(Uri uri, String documentId, String name, long size,
                              ArrayList<Song> found, HashSet<String> seen,
                              HashMap<String, Song> previous) {
        String authority = uri.getAuthority() == null ? "" : uri.getAuthority();
        String key = authority + "|" + documentId;
        String altKey = (name != null && size > 0)
                ? "n|" + name.toLowerCase(Locale.ROOT) + "|" + size
                : null;

        if (seen.contains(key)) return;
        if (altKey != null && seen.contains(altKey)) return;

        seen.add(key);
        if (altKey != null) seen.add(altKey);

        Song song = previous.get(key);
        if (song == null) {
            song = new Song(uri, key, titleFromFileName(name));
        }

        found.add(song);
    }

    private String titleFromFileName(String name) {
        if (name == null || name.trim().isEmpty()) return "Unknown song";

        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            return name.substring(0, dot);
        }

        return name;
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
                || lower.endsWith(".amr")
                || lower.endsWith(".3gp");
    }

    // --------------------------------------------------------------- metadata

    private void loadMetadata(final ArrayList<Song> songs, final int generation) {
        new Thread(() -> {
            ArrayList<MetaResult> batch = new ArrayList<>();

            for (Song song : songs) {
                if (generation != scanGeneration) return;
                if (song.metadataLoaded) continue;

                batch.add(readMetadata(song));

                if (batch.size() >= 25) {
                    postMetadata(batch, generation);
                    batch = new ArrayList<>();
                }
            }

            if (!batch.isEmpty()) {
                postMetadata(batch, generation);
            }
        }).start();
    }

    private MetaResult readMetadata(Song song) {
        String title = null;
        String artist = null;
        long durationMs = 0;

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();

        try {
            retriever.setDataSource(this, song.uri);
            title = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_TITLE);
            artist = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_ARTIST);

            String durationText = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durationText != null) {
                durationMs = Long.parseLong(durationText);
            }
        } catch (Exception ignored) {
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }

        return new MetaResult(song, title, artist, durationMs);
    }

    private void postMetadata(final ArrayList<MetaResult> batch,
                              final int generation) {
        runOnUiThread(() -> {
            if (generation != scanGeneration || isFinishing() || isDestroyed()) {
                return;
            }

            for (MetaResult result : batch) {
                result.song.metadataLoaded = true;

                if (result.title != null && !result.title.trim().isEmpty()) {
                    result.song.title = result.title.trim();
                }
                if (result.artist != null && !result.artist.trim().isEmpty()) {
                    result.song.artist = result.artist.trim();
                }
                if (result.durationMs > 0) {
                    result.song.durationMs = result.durationMs;
                }
            }

            adapter.notifyDataSetChanged();
            updatePlayerUi();
        });
    }

    // ------------------------------------------------------------ permissions

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return;

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        if (prefs.getBoolean(ASKED_NOTIFICATIONS_KEY, false)) return;

        prefs.edit().putBoolean(ASKED_NOTIFICATIONS_KEY, true).apply();
        requestPermissions(
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_NOTIFICATIONS);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_NOTIFICATIONS
                && (grantResults.length == 0
                || grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
            Toast.makeText(this,
                    "Notifications are off, so the playback controls will not "
                            + "show in the notification bar. Music still plays "
                            + "in the background.",
                    Toast.LENGTH_LONG).show();
        }
    }
                }
                              
