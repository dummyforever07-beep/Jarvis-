package com.minijarvis.app.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.minijarvis.app.R;
import com.minijarvis.app.ui.MainActivity;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Service for downloading the AI model from the internet
 */
public class ModelDownloadService extends Service {
    private static final String TAG = "ModelDownloadService";
    private static final String CHANNEL_ID = "ModelDownload";
    private static final int NOTIFICATION_ID = 1002;
    
    private static final String MODEL_URL = "https://huggingface.co/leliuga/ggml-gemma-2b-v1-q4_0/resolve/main/gemma-2b-v1-q4_0.gguf";
    private static final String MODEL_FILE = "gemma-2b-q4_0.gguf";
    
    private NotificationManager notificationManager;
    private Thread downloadThread;
    
    public static Intent getDownloadIntent() {
        return new Intent("com.minijarvis.app.action.DOWNLOAD_MODEL");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel();
        Log.i(TAG, "ModelDownloadService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Starting model download service");
        
        // Start download in foreground
        startForeground(NOTIFICATION_ID, createNotification("Initializing download...", 0));
        
        // Start download thread
        downloadThread = new Thread(() -> {
            try {
                downloadModel();
            } catch (Exception e) {
                Log.e(TAG, "Error downloading model", e);
                showNotification("Download failed", "Please try again");
            }
        });
        downloadThread.start();
        
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (downloadThread != null) {
            downloadThread.interrupt();
        }
        Log.i(TAG, "ModelDownloadService destroyed");
    }

    private void downloadModel() {
        try {
            File modelFile = new File(getFilesDir(), MODEL_FILE);
            
            // Check if already exists
            if (modelFile.exists()) {
                Log.i(TAG, "Model file already exists");
                showNotification("Model ready", "You can now use MiniJarvis!");
                stopForeground(false);
                stopSelf();
                return;
            }
            
            Log.i(TAG, "Starting model download from: " + MODEL_URL);
            showNotification("Downloading model...", 0);
            
            URL url = new URL(MODEL_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.connect();

            int fileLength = connection.getContentLength();
            InputStream input = new BufferedInputStream(connection.getInputStream());
            FileOutputStream output = new FileOutputStream(modelFile);

            byte[] buffer = new byte[8192];
            long total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                
                // Update progress
                int progress = (int) (total * 100 / fileLength);
                showNotification("Downloading model...", progress);
                
                output.write(buffer, 0, count);
            }

            output.flush();
            output.close();
            input.close();
            connection.disconnect();
            
            Log.i(TAG, "Model downloaded successfully: " + total + " bytes");
            showNotification("Download complete", "MiniJarvis is ready!");
            
            // Wait a bit then stop
            Thread.sleep(2000);
            stopForeground(false);
            stopSelf();
            
        } catch (InterruptedException e) {
            Log.i(TAG, "Download interrupted");
        } catch (Exception e) {
            Log.e(TAG, "Download failed", e);
            showNotification("Download failed", e.getMessage());
            stopForeground(false);
            stopSelf();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Model Download",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Downloading MiniJarvis AI model");
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification(String text, int progress) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, 
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? 
                        PendingIntent.FLAG_IMMUTABLE : 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("MiniJarvis")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_jarvis)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setAutoCancel(false);

        if (progress > 0) {
            builder.setProgress(100, progress, false);
        } else {
            builder.setProgress(100, 0, true);
        }

        return builder.build();
    }

    private void showNotification(String title, String text) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_jarvis)
                .setAutoCancel(true)
                .build();

        notificationManager.notify(NOTIFICATION_ID, notification);
    }
}