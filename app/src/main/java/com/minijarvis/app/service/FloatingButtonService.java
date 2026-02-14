package com.minijarvis.app.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.minijarvis.app.R;
import com.minijarvis.app.ui.MainActivity;

/**
 * Foreground service that manages the floating button overlay
 */
public class FloatingButtonService extends Service {
    private static final String TAG = "FloatingButtonService";
    private static final String CHANNEL_ID = "MiniJarvisForeground";
    private static final int NOTIFICATION_ID = 1001;
    
    private WindowManager windowManager;
    private View floatingButtonView;
    private Vibrator vibrator;
    private boolean isFloatingButtonAdded = false;
    
    // UI callbacks
    public interface FloatingButtonListener {
        void onButtonClicked();
        void onEmergencyStop();
    }
    
    private FloatingButtonListener buttonListener;
    
    public static Intent getStartIntent(Context context) {
        return new Intent(context, FloatingButtonService.class);
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        
        Log.i(TAG, "FloatingButtonService created");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "FloatingButtonService started");
        
        // Check overlay permission
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "No overlay permission");
            Toast.makeText(this, "Please grant overlay permission for MiniJarvis", Toast.LENGTH_LONG).show();
            stopSelf();
            return START_NOT_STICKY;
        }
        
        // Create notification channel
        createNotificationChannel();
        
        // Create foreground notification
        Notification notification = createNotification();
        startForeground(NOTIFICATION_ID, notification);
        
        // Create floating button
        createFloatingButton();
        
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        Log.i(TAG, "FloatingButtonService destroying");
        
        // Remove floating button
        if (floatingButtonView != null && isFloatingButtonAdded) {
            try {
                windowManager.removeView(floatingButtonView);
                isFloatingButtonAdded = false;
            } catch (Exception e) {
                Log.e(TAG, "Error removing floating button", e);
            }
        }
        
        super.onDestroy();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    private void createFloatingButton() {
        try {
            // Inflate the floating button layout
            LayoutInflater inflater = LayoutInflater.from(this);
            floatingButtonView = inflater.inflate(R.layout.floating_button, null);
            
            // Configure button parameters
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                            WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
            );
            
            params.gravity = Gravity.TOP | Gravity.END;
            params.x = 16; // 16dp from right edge
            params.y = 100; // Start from top
            
            // Set up button click and drag handling
            setupButtonInteractions(params);
            
            // Add to window manager
            windowManager.addView(floatingButtonView, params);
            isFloatingButtonAdded = true;
            
            Log.i(TAG, "Floating button added");
        } catch (Exception e) {
            Log.e(TAG, "Error creating floating button", e);
        }
    }
    
    private void setupButtonInteractions(WindowManager.LayoutParams params) {
        ImageView buttonImage = floatingButtonView.findViewById(R.id.floatingButtonImage);
        View emergencyButton = floatingButtonView.findViewById(R.id.emergencyButton);
        
        // Handle floating button clicks
        buttonImage.setOnClickListener(v -> {
            vibrate();
            if (buttonListener != null) {
                buttonListener.onButtonClicked();
            }
        });
        
        // Handle emergency stop button
        emergencyButton.setOnClickListener(v -> {
            vibrate();
            if (buttonListener != null) {
                buttonListener.onEmergencyStop();
            }
        });
        
        // Handle drag gestures
        buttonImage.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    // Store initial touch position
                    params.x = (int) (event.getRawX() - v.getWidth() / 2);
                    params.y = (int) (event.getRawY() - v.getHeight() / 2);
                    updateViewPosition(params);
                    return true;
                    
                case MotionEvent.ACTION_MOVE:
                    // Update position while dragging
                    params.x = (int) (event.getRawX() - v.getWidth() / 2);
                    params.y = (int) (event.getRawY() - v.getHeight() / 2);
                    updateViewPosition(params);
                    return true;
                    
                case MotionEvent.ACTION_UP:
                    v.setPressed(false);
                    return true;
            }
            return false;
        });
    }
    
    private void updateViewPosition(WindowManager.LayoutParams params) {
        try {
            windowManager.updateViewLayout(floatingButtonView, params);
        } catch (Exception e) {
            Log.e(TAG, "Error updating view position", e);
        }
    }
    
    private void vibrate() {
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(50); // 50ms vibration
        }
    }
    
    private Notification createNotification() {
        // Create intent for notification click
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, 
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? 
                        PendingIntent.FLAG_IMMUTABLE : 0);
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(R.drawable.ic_jarvis)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setAutoCancel(false)
                .build();
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.notification_channel_description));
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    /**
     * Set listener for button events
     */
    public void setFloatingButtonListener(FloatingButtonListener listener) {
        this.buttonListener = listener;
    }
    
    /**
     * Update the floating button state
     */
    public void updateButtonState(String status) {
        if (floatingButtonView != null) {
            // Update button appearance based on status
            ImageView buttonImage = floatingButtonView.findViewById(R.id.floatingButtonImage);
            if (buttonImage != null) {
                if (status.equals("processing")) {
                    buttonImage.setColorFilter(getResources().getColor(R.color.warning));
                } else if (status.equals("ready")) {
                    buttonImage.clearColorFilter();
                } else if (status.equals("error")) {
                    buttonImage.setColorFilter(getResources().getColor(R.color.error));
                }
            }
        }
    }
    
    /**
     * Show a temporary debug panel
     */
    public void showDebugPanel(String uiJson, String modelOutput, String currentApp) {
        if (floatingButtonView != null) {
            View debugPanel = floatingButtonView.findViewById(R.id.debugPanel);
            if (debugPanel != null) {
                debugPanel.setVisibility(View.VISIBLE);
                
                // Update debug info
                android.widget.TextView uiText = debugPanel.findViewById(R.id.uiJsonText);
                android.widget.TextView outputText = debugPanel.findViewById(R.id.modelOutputText);
                android.widget.TextView appText = debugPanel.findViewById(R.id.currentAppText);
                
                if (uiText != null) uiText.setText(uiJson);
                if (outputText != null) outputText.setText(modelOutput);
                if (appText != null) appText.setText(currentApp);
                
                // Auto-hide after 5 seconds
                debugPanel.postDelayed(() -> {
                    if (debugPanel != null && debugPanel.getVisibility() == View.VISIBLE) {
                        debugPanel.setVisibility(View.GONE);
                    }
                }, 5000);
            }
        }
    }
}