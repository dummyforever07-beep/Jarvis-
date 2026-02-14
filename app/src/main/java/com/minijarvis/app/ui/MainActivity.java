package com.minijarvis.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.minijarvis.app.R;
import com.minijarvis.app.accessibility.MiniJarvisAccessibilityService;
import com.minijarvis.app.llm.LLMEngine;
import com.minijarvis.app.llm.MockLLMEngine;
import com.minijarvis.app.model.ActionModel;
import com.minijarvis.app.model.UIStructure;
import com.minijarvis.app.service.FloatingButtonService;
import com.minijarvis.app.service.ModelDownloadService;
import com.minijarvis.app.util.ActionExecutor;

/**
 * Main activity for debugging and controls
 */
public class MainActivity extends AppCompatActivity implements 
        MiniJarvisAccessibilityService.UIStructureCallback {
    
    private static final String TAG = "MainActivity";
    private static final int OVERLAY_PERMISSION_REQUEST_CODE = 1;
    private static final int ACCESSIBILITY_PERMISSION_REQUEST_CODE = 2;
    private static final int MODEL_DOWNLOAD_REQUEST_CODE = 3;
    
    // UI components
    private EditText instructionInput;
    private Button processButton;
    private Button startServiceButton;
    private Button stopServiceButton;
    private Button clearLogsButton;
    private Button downloadModelButton;
    private TextView modelStatusText;
    private TextView statusText;
    private TextView currentAppText;
    private TextView uiJsonText;
    private TextView modelOutputText;
    private TextView statusLogText;
    
    // Core components
    private MiniJarvisAccessibilityService accessibilityService;
    private FloatingButtonService floatingButtonService;
    private LLMEngine llmEngine;
    private MockLLMEngine mockLlmEngine;
    private ActionExecutor actionExecutor;
    private ActionExecutor.ActionTracker actionTracker = new ActionExecutor.ActionTracker();
    
    // State
    private Gson gson = new Gson();
    private UIStructure currentUIStructure;
    private boolean serviceRunning = false;
    
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize LLM engine to check for model
        llmEngine = new LLMEngine(this);
        
        // Check model before showing UI
        if (!llmEngine.isModelDownloaded()) {
            // Launch download activity immediately
            Intent intent = new Intent(this, ModelDownloadActivity.class);
            startActivity(intent);
            // Finish this activity so user can't use app without model
            finish();
            return;
        }
        
        setContentView(R.layout.activity_main);
        
        initializeComponents();
        setupUI();
        checkPermissions();
        
        Log.i(TAG, "MainActivity created");
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        checkPermissions();
        
        // Refresh accessibility service instance
        accessibilityService = MiniJarvisAccessibilityService.getInstance();
        
        // Check if model is now available after download
        if (llmEngine != null) {
            updateModelStatus();
            if (llmEngine.isModelDownloaded()) {
                processButton.setEnabled(true);
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanupComponents();
    }
    
    private void initializeComponents() {
        // Initialize LLM engines (llmEngine already initialized in onCreate if model exists)
        if (llmEngine == null) {
            llmEngine = new LLMEngine(this);
        }
        mockLlmEngine = new MockLLMEngine(this);
        
        // Initialize mock engine (for demo purposes)
        boolean llmInitialized = mockLlmEngine.initialize();
        Log.i(TAG, "Mock LLM initialized: " + llmInitialized);
        
        // Get accessibility service instance
        accessibilityService = MiniJarvisAccessibilityService.getInstance();
        actionExecutor = new ActionExecutor(accessibilityService);
    }
    
    private void setupUI() {
        instructionInput = findViewById(R.id.instructionInput);
        processButton = findViewById(R.id.processButton);
        startServiceButton = findViewById(R.id.startServiceButton);
        stopServiceButton = findViewById(R.id.stopServiceButton);
        clearLogsButton = findViewById(R.id.clearLogsButton);
        downloadModelButton = findViewById(R.id.downloadModelButton);
        modelStatusText = findViewById(R.id.modelStatusText);
        statusText = findViewById(R.id.statusText);
        currentAppText = findViewById(R.id.currentAppText);
        uiJsonText = findViewById(R.id.uiJsonText);
        modelOutputText = findViewById(R.id.modelOutputText);
        statusLogText = findViewById(R.id.statusLogText);
        
        // Setup button click listeners
        processButton.setOnClickListener(v -> processInstruction());
        startServiceButton.setOnClickListener(v -> startServices());
        stopServiceButton.setOnClickListener(v -> stopServices());
        clearLogsButton.setOnClickListener(v -> clearLogs());
        downloadModelButton.setOnClickListener(v -> downloadModel());
        
        // Check model status
        updateModelStatus();
        
        updateStatus("Ready");
        appendLog("MiniJarvis initialized. Grant permissions and start service.");
    }
    
    private void checkPermissions() {
        // Check Accessibility permission
        if (!isAccessibilityServiceEnabled()) {
            appendLog("Accessibility permission not granted");
        }
        
        // Check Overlay permission
        if (!Settings.canDrawOverlays(this)) {
            appendLog("Overlay permission not granted");
        }
    }
    
    private boolean isAccessibilityServiceEnabled() {
        String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        
        if (enabledServices != null) {
            return enabledServices.contains(getPackageName() + "/.accessibility.MiniJarvisAccessibilityService");
        }
        return false;
    }
    
    private void requestAccessibilityPermission() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
    }
    
    private void requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            return; // Already have permission
        }
        
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Settings.canDrawOverlays(this)) {
                appendLog("Overlay permission granted");
            } else {
                appendLog("Overlay permission denied");
            }
        }
        
        // Check if model download completed successfully
        if (requestCode == MODEL_DOWNLOAD_REQUEST_CODE && resultCode == RESULT_OK) {
            appendLog("Model download completed");
            updateModelStatus();
            processButton.setEnabled(true);
        }
    }
    
    private void startServices() {
        try {
            // Start Floating Button Service
            Intent serviceIntent = FloatingButtonService.getStartIntent(this);
            startForegroundService(serviceIntent);
            
            serviceRunning = true;
            updateStatus("Services started");
            appendLog("Services started successfully");
            
            // Setup floating button listener
            // Note: This would need proper binding to work in real implementation
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting services", e);
            updateStatus("Error starting services");
            appendLog("Error: " + e.getMessage());
        }
    }
    
    private void stopServices() {
        try {
            // Stop services
            stopService(new Intent(this, FloatingButtonService.class));
            
            serviceRunning = false;
            updateStatus("Services stopped");
            appendLog("Services stopped");
            
        } catch (Exception e) {
            Log.e(TAG, "Error stopping services", e);
            updateStatus("Error stopping services");
            appendLog("Error: " + e.getMessage());
        }
    }
    
    private void processInstruction() {
        String instruction = instructionInput.getText().toString().trim();
        
        if (instruction.isEmpty()) {
            Toast.makeText(this, "Please enter an instruction", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (!serviceRunning) {
            Toast.makeText(this, "Please start services first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Use main thread for UI updates
        mainHandler.post(() -> {
            try {
                updateStatus("Processing...");
                
                // Get accessibility service instance (may have changed)
                accessibilityService = MiniJarvisAccessibilityService.getInstance();
                actionExecutor = new ActionExecutor(accessibilityService);
                
                // Get current UI structure
                if (accessibilityService == null) {
                    appendLog("Accessibility service not available");
                    updateStatus("Error");
                    return;
                }
                
                currentUIStructure = accessibilityService.extractCurrentUI();
                if (currentUIStructure == null) {
                    appendLog("Could not extract UI structure");
                    updateStatus("Error");
                    return;
                }
                
                // Update UI with current structure
                updateUIStructureDisplay(currentUIStructure);
                
                // Generate action using LLM
                ActionModel action = mockLlmEngine.generateAction(instruction, currentUIStructure);
                if (action == null || !action.isValid()) {
                    appendLog("Failed to generate action");
                    updateStatus("Error");
                    return;
                }
                
                // Update model output display
                String actionJson = gson.toJson(action);
                modelOutputText.setText(actionJson);
                
                appendLog("Generated action: " + action.action);
                
                // Check for throttle
                if (actionTracker.shouldThrottle(action.action, action.target, action.text)) {
                    appendLog("Action throttled (duplicate)");
                    updateStatus("Ready");
                    return;
                }
                
                // Validate and execute action
                if (actionExecutor.executeAction(action, currentUIStructure)) {
                    actionTracker.recordAction(action.action, action.target, action.text);
                    appendLog("Action executed successfully: " + action.action + " " + action.target);
                    updateStatus("Action executed");
                } else {
                    appendLog("Failed to execute action");
                    updateStatus("Error");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error processing instruction", e);
                appendLog("Error: " + e.getMessage());
                updateStatus("Error");
            }
        });
    }
    
    private void updateUIStructureDisplay(UIStructure uiStructure) {
        if (uiStructure != null) {
            currentAppText.setText(uiStructure.app);
            String uiJson = gson.toJson(uiStructure);
            uiJsonText.setText(uiJson);
        }
    }
    
    private void updateStatus(String status) {
        statusText.setText(status);
    }
    
    private void appendLog(String message) {
        String timestamp = String.format("%02d:%02d",
                System.currentTimeMillis() / 60000 % 60,
                System.currentTimeMillis() / 1000 % 60);
        String logEntry = "[" + timestamp + "] " + message + "\n";
        
        String currentLog = statusLogText.getText().toString();
        if (currentLog.length() > 2000) {
            // Trim log to last 1000 characters
            currentLog = currentLog.substring(currentLog.length() - 1000);
        }
        
        statusLogText.setText(currentLog + logEntry);
    }
    
    private void clearLogs() {
        statusLogText.setText("");
    }
    
    private void updateModelStatus() {
        boolean modelDownloaded = llmEngine.isModelDownloaded();
        
        if (modelDownloaded) {
            modelStatusText.setText("✅ Model downloaded - AI ready");
            downloadModelButton.setText("Redownload Model");
            downloadModelButton.setEnabled(true);
        } else {
            modelStatusText.setText("❌ Model not downloaded - Download required");
            downloadModelButton.setText("Download Model (2GB)");
            downloadModelButton.setEnabled(true);
            processButton.setEnabled(false);
        }
    }
    
    private void downloadModel() {
        appendLog("Launching model download...");
        
        // Launch ModelDownloadActivity
        Intent intent = new Intent(this, ModelDownloadActivity.class);
        startActivityForResult(intent, MODEL_DOWNLOAD_REQUEST_CODE);
        
        Toast.makeText(this, "Model download will start. This may take several minutes.", Toast.LENGTH_LONG).show();
    }
    
    @Override
    public void onUIStructureChanged(UIStructure uiStructure) {
        // Called when UI structure changes (throttled by accessibility service)
        mainHandler.post(() -> {
            currentUIStructure = uiStructure;
            updateUIStructureDisplay(uiStructure);
            appendLog("UI updated: " + uiStructure.app);
        });
    }
    
    private void cleanupComponents() {
        if (llmEngine != null) {
            llmEngine.cleanup();
        }
        if (mockLlmEngine != null) {
            mockLlmEngine.cleanup();
        }
    }
}