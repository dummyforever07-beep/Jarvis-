package com.minijarvis.app.util;

import android.util.Log;
import com.minijarvis.app.accessibility.MiniJarvisAccessibilityService;
import com.minijarvis.app.model.ActionModel;
import com.minijarvis.app.model.UIStructure;

/**
 * Executes validated actions using Accessibility Service
 */
public class ActionExecutor {
    private static final String TAG = "ActionExecutor";
    
    private final MiniJarvisAccessibilityService accessibilityService;
    
    public ActionExecutor(MiniJarvisAccessibilityService accessibilityService) {
        this.accessibilityService = accessibilityService;
    }
    
    /**
     * Execute an action after validating it against current UI
     */
    public boolean executeAction(ActionModel action, UIStructure currentUI) {
        if (!action.isValid()) {
            Log.w(TAG, "Invalid action provided");
            return false;
        }
        
        Log.i(TAG, "Executing action: " + action.action + " target: " + action.target + " text: " + action.text);
        
        try {
            switch (action.action) {
                case ActionModel.ACTION_CLICK:
                    return executeClick(action, currentUI);
                    
                case ActionModel.ACTION_TYPE:
                    return executeType(action, currentUI);
                    
                case ActionModel.ACTION_SCROLL:
                    return executeScroll(action, currentUI);
                    
                case ActionModel.ACTION_OPEN_APP:
                    return executeOpenApp(action);
                    
                case ActionModel.ACTION_GO_BACK:
                    return executeBack();
                    
                case ActionModel.ACTION_NOTHING:
                    Log.i(TAG, "Action is nothing - no execution needed");
                    return true;
                    
                default:
                    Log.w(TAG, "Unknown action type: " + action.action);
                    return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error executing action", e);
            return false;
        }
    }
    
    private boolean executeClick(ActionModel action, UIStructure currentUI) {
        String target = action.target;
        
        // Validate target exists
        if (!isValidTarget(target, currentUI.clickable, currentUI.textFields)) {
            Log.w(TAG, "Invalid click target: " + target);
            return false;
        }
        
        // Execute click
        accessibilityService.performClick(target);
        
        // Add delay as specified in requirements
        sleep(500);
        
        Log.i(TAG, "Click executed successfully on: " + target);
        return true;
    }
    
    private boolean executeType(ActionModel action, UIStructure currentUI) {
        String target = action.target;
        String text = action.text;
        
        // Validate text is provided
        if (text == null || text.isEmpty()) {
            Log.w(TAG, "No text provided for type action");
            return false;
        }
        
        // Validate target is a text field
        if (!isTextFieldValid(target, currentUI.textFields, currentUI.focused)) {
            Log.w(TAG, "Invalid text field target: " + target);
            return false;
        }
        
        // Execute typing
        accessibilityService.performType(target, text);
        
        // Add delay as specified
        sleep(500);
        
        Log.i(TAG, "Type executed successfully in: " + target);
        return true;
    }
    
    private boolean executeScroll(ActionModel action, UIStructure currentUI) {
        String direction = action.target;
        
        // Validate direction
        if (direction == null || direction.isEmpty()) {
            direction = "forward"; // default
        }
        
        if (!direction.equals("forward") && !direction.equals("backward")) {
            Log.w(TAG, "Invalid scroll direction: " + direction);
            direction = "forward"; // default to forward
        }
        
        // Execute scroll
        accessibilityService.performScroll(direction);
        
        // Add delay
        sleep(500);
        
        Log.i(TAG, "Scroll executed successfully: " + direction);
        return true;
    }
    
    private boolean executeOpenApp(ActionModel action) {
        String appName = action.target;
        
        if (appName == null || appName.isEmpty()) {
            Log.w(TAG, "No app name provided for open_app action");
            return false;
        }
        
        // Note: Opening apps would require Intent-based approach
        // For now, we'll implement a simple approach using accessibility to find and click app launcher
        // This is a simplified implementation
        
        Log.i(TAG, "Open app action requested for: " + appName);
        
        // TODO: Implement proper app opening logic
        // This would need to search for the app in launcher/recents
        sleep(500);
        
        return true; // Return true for now as this is complex to implement without additional permissions
    }
    
    private boolean executeBack() {
        accessibilityService.performBack();
        
        // Add delay
        sleep(500);
        
        Log.i(TAG, "Back action executed successfully");
        return true;
    }
    
    private boolean isValidTarget(String target, String[] clickable, String[] textFields) {
        if (target == null || target.isEmpty()) {
            return false;
        }
        
        // Check clickable elements
        if (clickable != null) {
            for (String element : clickable) {
                if (element.equals(target)) {
                    return true;
                }
            }
        }
        
        // Also allow clicking on text fields
        return isTextFieldValid(target, textFields, null);
    }
    
    private boolean isTextFieldValid(String target, String[] textFields, String focused) {
        if (target == null || target.isEmpty()) {
            return false;
        }
        
        // Check if it's the focused field
        if (focused != null && focused.equals(target)) {
            return true;
        }
        
        // Check text fields array
        if (textFields != null) {
            for (String field : textFields) {
                if (field.equals(target)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "Sleep interrupted", e);
        }
    }
    
    /**
     * Prevent repeated identical actions
     */
    public static class ActionTracker {
        private String lastAction;
        private String lastTarget;
        private String lastText;
        private long lastExecutionTime;
        
        private static final long THROTTLE_DURATION = 1000; // 1 second throttle
        
        public boolean shouldThrottle(String action, String target, String text) {
            long currentTime = System.currentTimeMillis();
            
            if (lastAction != null && lastAction.equals(action) &&
                lastTarget != null && lastTarget.equals(target) &&
                (lastText == null ? text == null : lastText.equals(text))) {
                
                if (currentTime - lastExecutionTime < THROTTLE_DURATION) {
                    return true; // Should throttle
                }
            }
            
            return false;
        }
        
        public void recordAction(String action, String target, String text) {
            this.lastAction = action;
            this.lastTarget = target;
            this.lastText = text;
            this.lastExecutionTime = System.currentTimeMillis();
        }
        
        public void reset() {
            this.lastAction = null;
            this.lastTarget = null;
            this.lastText = null;
            this.lastExecutionTime = 0;
        }
    }
}