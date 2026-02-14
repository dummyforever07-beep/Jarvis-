package com.minijarvis.app.ui;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.app.AppCompatActivity;

import com.minijarvis.app.R;

import java.io.File;
import java.text.DecimalFormat;

/**
 * Activity for downloading the AI model with progress tracking
 * Uses Android DownloadManager for reliable, resumable downloads
 */
public class ModelDownloadActivity extends AppCompatActivity {
    private static final String TAG = "ModelDownloadActivity";
    
    // Model configuration
    private static final String MODEL_FILE = "gemma-2b-q4.gguf";
    private static final String MODEL_URL = "https://huggingface.co/leliuga/ggml-gemma-2b-v1-q4_0/resolve/main/gemma-2b-v1-q4_0.gguf";
    private static final long MIN_FREE_STORAGE = 3L * 1024 * 1024 * 1024; // 3GB
    private static final long MIN_AVAILABLE_RAM = 3L * 1024 * 1024 * 1024; // 3GB
    
    // Download preferences
    private static final String PREFS_NAME = "download_prefs";
    private static final String PREF_WIFI_ONLY = "wifi_only";
    private static final String PREF_DOWNLOAD_ID = "download_id";
    
    // UI Components
    private ProgressBar progressBar;
    private TextView progressPercentText;
    private TextView progressSizeText;
    private TextView etaText;
    private TextView statusText;
    private TextView errorText;
    private Button downloadButton;
    private Button cancelButton;
    private SwitchCompat wifiOnlySwitch;
    private LinearLayout errorContainer;
    
    // Download tracking
    private DownloadManager downloadManager;
    private long downloadId = -1;
    private BroadcastReceiver downloadReceiver;
    private Handler mainHandler;
    private SharedPreferences preferences;
    private boolean isDownloading = false;
    private boolean isPaused = false;
    
    // Progress tracking
    private long totalBytes = 0;
    private long downloadedBytes = 0;
    private long startTime = 0;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_model_download);
        
        initializeComponents();
        setupUI();
        setupListeners();
        checkPrerequisites();
        
        Log.i(TAG, "ModelDownloadActivity created");
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (isDownloading) {
            startProgressTracking();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Don't stop tracking in onPause as activity might be resumed
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (downloadReceiver != null) {
            unregisterReceiver(downloadReceiver);
        }
    }
    
    private void initializeComponents() {
        mainHandler = new Handler(Looper.getMainLooper());
        downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    private void setupUI() {
        progressBar = findViewById(R.id.downloadProgressBar);
        progressPercentText = findViewById(R.id.progressPercentText);
        progressSizeText = findViewById(R.id.progressSizeText);
        etaText = findViewById(R.id.etaText);
        statusText = findViewById(R.id.statusText);
        errorText = findViewById(R.id.errorText);
        downloadButton = findViewById(R.id.downloadButton);
        cancelButton = findViewById(R.id.cancelButton);
        wifiOnlySwitch = findViewById(R.id.wifiOnlySwitch);
        errorContainer = findViewById(R.id.errorContainer);
        
        // Load WiFi preference
        boolean wifiOnly = preferences.getBoolean(PREF_WIFI_ONLY, true);
        wifiOnlySwitch.setChecked(wifiOnly);
        
        // Check if model already exists
        if (isModelDownloaded()) {
            downloadButton.setText("Redownload Model");
        }
        
        Log.i(TAG, "UI setup complete");
    }
    
    private void setupListeners() {
        downloadButton.setOnClickListener(v -> {
            if (isDownloading) {
                pauseDownload();
            } else {
                startDownload();
            }
        });
        
        cancelButton.setOnClickListener(v -> {
            if (isDownloading) {
                cancelDownload();
            } else {
                finish();
            }
        });
        
        wifiOnlySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(PREF_WIFI_ONLY, isChecked).apply();
            if (isChecked && !isWifiAvailable()) {
                showError("WiFi only mode enabled but no WiFi connection");
            }
        });
    }
    
    private void checkPrerequisites() {
        // Check storage
        if (!hasMinimumStorage()) {
            showError(getString(R.string.storage_error_message));
            downloadButton.setEnabled(false);
            return;
        }
        
        // Check RAM
        if (!hasMinimumRAM()) {
            showError(getString(R.string.ram_error_message));
            downloadButton.setEnabled(false);
            return;
        }
        
        // Check network if WiFi only
        if (wifiOnlySwitch.isChecked() && !isWifiAvailable()) {
            showError("WiFi connection required for download");
        }
    }
    
    private boolean hasMinimumStorage() {
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            long availableBytes = stat.getAvailableBytes();
            return availableBytes >= MIN_FREE_STORAGE;
        } catch (Exception e) {
            Log.e(TAG, "Error checking storage", e);
            return true; // Assume available if can't check
        }
    }
    
    private boolean hasMinimumRAM() {
        try {
            android.app.ActivityManager.MemoryInfo memInfo = new android.app.ActivityManager.MemoryInfo();
            android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            am.getMemoryInfo(memInfo);
            
            long availableBytes = memInfo.availMem;
            Log.i(TAG, "Available RAM: " + (availableBytes / (1024 * 1024 * 1024)) + "GB");
            return availableBytes >= MIN_AVAILABLE_RAM;
        } catch (Exception e) {
            Log.e(TAG, "Error checking RAM", e);
            return true; // Assume available if can't check
        }
    }
    
    private boolean isWifiAvailable() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network activeNetwork = cm.getActiveNetwork();
                if (activeNetwork == null) return false;
                
                NetworkCapabilities capabilities = cm.getNetworkCapabilities(activeNetwork);
                return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            } else {
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                return activeNetwork != null && activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking WiFi", e);
            return true; // Assume available if can't check
        }
    }
    
    private boolean isModelDownloaded() {
        File modelFile = new File(getFilesDir(), MODEL_FILE);
        return modelFile.exists() && modelFile.length() > 1000000; // At least 1MB
    }
    
    private void startDownload() {
        if (wifiOnlySwitch.isChecked() && !isWifiAvailable()) {
            showError("WiFi connection required for download");
            return;
        }
        
        hideError();
        
        // Clean up any existing download
        if (downloadId != -1) {
            downloadManager.remove(downloadId);
        }
        
        Log.i(TAG, "Starting model download");
        statusText.setText(getString(R.string.download_initializing));
        downloadButton.setText("Pause");
        
        // Create download request
        DownloadManager.Request request = new DownloadManager.Request(android.net.Uri.parse(MODEL_URL));
        request.setTitle("MiniJarvis AI Model");
        request.setDescription("Downloading AI engine model");
        request.setMimeType("application/octet-stream");
        
        // Use app-private storage
        File filesDir = getFilesDir();
        File modelDir = new File(filesDir, "models");
        if (!modelDir.exists()) {
            modelDir.mkdirs();
        }
        File modelFile = new File(modelDir, MODEL_FILE);
        
        request.setDestinationUri(android.net.Uri.fromFile(modelFile));
        
        // Set notification
        request.setNotificationVisibility(
            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
        );
        
        // Allow network type based on preference
        if (wifiOnlySwitch.isChecked()) {
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI);
        } else {
            request.setAllowedNetworkTypes(
                DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE
            );
        }
        
        // Start download
        downloadId = downloadManager.enqueue(request);
        preferences.edit().putLong(PREF_DOWNLOAD_ID, downloadId).apply();
        
        isDownloading = true;
        isPaused = false;
        startTime = System.currentTimeMillis();
        
        startProgressTracking();
        registerDownloadReceiver();
    }
    
    private void pauseDownload() {
        if (downloadId != -1) {
            downloadManager.remove(downloadId);
            isDownloading = false;
            isPaused = true;
            downloadButton.setText("Resume");
            statusText.setText(getString(R.string.download_paused));
            Log.i(TAG, "Download paused");
        }
    }
    
    private void cancelDownload() {
        if (downloadId != -1) {
            downloadManager.remove(downloadId);
            downloadId = -1;
            preferences.edit().remove(PREF_DOWNLOAD_ID).apply();
        }
        isDownloading = false;
        isPaused = false;
        resetProgress();
        Log.i(TAG, "Download cancelled");
        
        // Close activity if not auto-retrying
        if (!isModelDownloaded()) {
            finish();
        }
    }
    
    private void startProgressTracking() {
        new Thread(() -> {
            while (isDownloading && downloadId != -1) {
                try {
                    DownloadManager.Query query = new DownloadManager.Query();
                    query.setFilterById(downloadId);
                    
                    android.database.Cursor cursor = downloadManager.query(query);
                    if (cursor != null && cursor.moveToFirst()) {
                        int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                        long totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                        long downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                        
                        updateProgress(totalBytes, downloaded, status);
                    }
                    
                    if (cursor != null) {
                        cursor.close();
                    }
                    
                    Thread.sleep(1000); // Update every second
                } catch (Exception e) {
                    Log.e(TAG, "Error tracking download", e);
                    break;
                }
            }
        }).start();
    }
    
    private void updateProgress(long total, long downloaded, int status) {
        mainHandler.post(() -> {
            switch (status) {
                case DownloadManager.STATUS_RUNNING:
                    this.totalBytes = total;
                    this.downloadedBytes = downloaded;
                    
                    int progress = total > 0 ? (int) (downloaded * 100 / total) : 0;
                    progressBar.setProgress(progress);
                    progressPercentText.setText(progress + "%");
                    
                    // Format sizes
                    String downloadedStr = formatBytes(downloaded);
                    String totalStr = formatBytes(total);
                    progressSizeText.setText(getString(R.string.download_size, downloadedStr, totalStr));
                    
                    // Calculate ETA
                    long elapsed = System.currentTimeMillis() - startTime;
                    if (downloaded > 0 && elapsed > 5000) {
                        long remainingBytes = total - downloaded;
                        long bytesPerSecond = downloaded * 1000 / elapsed;
                        if (bytesPerSecond > 0) {
                            long remainingSeconds = remainingBytes / bytesPerSecond;
                            String eta = formatDuration(remainingSeconds);
                            etaText.setText(getString(R.string.download_eta, eta));
                        }
                    }
                    
                    statusText.setText(getString(R.string.download_progress, progress));
                    break;
                    
                case DownloadManager.STATUS_SUCCESSFUL:
                    handleDownloadSuccess();
                    break;
                    
                case DownloadManager.STATUS_FAILED:
                    handleDownloadFailure();
                    break;
                    
                case DownloadManager.STATUS_PAUSED:
                    isPaused = true;
                    isDownloading = false;
                    downloadButton.setText("Resume");
                    statusText.setText(getString(R.string.download_paused));
                    break;
            }
        });
    }
    
    private void handleDownloadSuccess() {
        Log.i(TAG, "Download completed successfully");
        isDownloading = false;
        isPaused = false;
        
        progressBar.setProgress(100);
        progressPercentText.setText("100%");
        statusText.setText(getString(R.string.download_complete));
        
        // Validate downloaded file
        if (isModelDownloaded()) {
            downloadButton.setText("Complete");
            downloadButton.setEnabled(false);
            
            // Auto-close after 2 seconds
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                setResult(Activity.RESULT_OK);
                finish();
            }, 2000);
        } else {
            showError(getString(R.string.model_corrupted));
        }
    }
    
    private void handleDownloadFailure() {
        Log.e(TAG, "Download failed");
        isDownloading = false;
        isPaused = false;
        
        showError(getString(R.string.download_failed));
        downloadButton.setText("Retry");
    }
    
    private void registerDownloadReceiver() {
        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long receivedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (receivedId == downloadId) {
                    int status = intent.getIntExtra(DownloadManager.EXTRA_NOTIFICATION_CLICK_DOWNLOAD_ID, -1);
                    Log.i(TAG, "Download receiver triggered with status: " + status);
                }
            }
        };
        
        registerReceiver(downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }
    
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
    
    private String formatDuration(long seconds) {
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        seconds = seconds % 60;
        if (minutes < 60) return minutes + "m " + seconds + "s";
        long hours = minutes / 60;
        minutes = minutes % 60;
        return hours + "h " + minutes + "m";
    }
    
    private void resetProgress() {
        progressBar.setProgress(0);
        progressPercentText.setText("0%");
        progressSizeText.setText("0 MB / 2000 MB");
        etaText.setText(R.string.download_eta);
        statusText.setText(getString(R.string.download_initializing));
    }
    
    private void showError(String message) {
        errorContainer.setVisibility(View.VISIBLE);
        errorText.setText(message);
        downloadButton.setEnabled(true);
        downloadButton.setText("Retry");
    }
    
    private void hideError() {
        errorContainer.setVisibility(View.GONE);
    }
}