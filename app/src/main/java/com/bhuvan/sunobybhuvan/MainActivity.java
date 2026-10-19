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
        Button previous = makeButton("⏮", pa
