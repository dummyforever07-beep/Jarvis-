package com.minijarvis.app.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.google.gson.Gson;
import com.minijarvis.app.model.UIStructure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Accessibility Service for UI extraction and action execution
 */
public class MiniJarvisAccessibilityService extends AccessibilityService {
    private static final String TAG = "MiniJarvisAccessibility";
    private static MiniJarvisAccessibilityService instance;
    private Gson gson = new Gson();
    
    // Callback interface for UI updates
    public interface UIStructureCallback {
        void onUIStructureChanged(UIStructure uiStructure);
    }
    
    private UIStructureCallback uiCallback;
    
    public static MiniJarvisAccessibilityService getInstance() {
        return instance;
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.i(TAG, "MiniJarvis Accessibility Service created");
    }
    
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "MiniJarvis Accessibility Service connected");
    }
    
    @Override
    public void onDestroy() {
        Log.i(TAG, "MiniJarvis Accessibility Service destroyed");
        instance = null;
        super.onDestroy();
    }
    
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Process UI changes and notify callback
        if (uiCallback != null) {
            UIStructure uiStructure = extractCurrentUI();
            if (uiStructure != null) {
                uiCallback.onUIStructureChanged(uiStructure);
            }
        }
    }
    
    @Override
    public void onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted");
    }
    
    /**
     * Extract current UI structure from accessibility nodes
     */
    public UIStructure extractCurrentUI() {
        try {
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode == null) {
                return null;
            }
            
            UIStructure uiStructure = extractFromNode(rootNode, getCurrentAppName(rootNode));
            rootNode.recycle();
            
            return uiStructure;
        } catch (Exception e) {
            Log.e(TAG, "Error extracting UI structure", e);
            return null;
        }
    }
    
    private UIStructure extractFromNode(AccessibilityNodeInfo node, String appName) {
        Set<String> clickableSet = new HashSet<>();
        Set<String> textFieldsSet = new HashSet<>();
        String focusedField = "";
        
        extractFromNodeRecursively(node, clickableSet, textFieldsSet);
        
        // Get focused field
        AccessibilityNodeInfo focused = node.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        if (focused != null) {
            focusedField = getNodeLabel(focused);
            focused.recycle();
        }
        
        // Convert sets to arrays
        String[] clickable = clickableSet.toArray(new String[0]);
        String[] textFields = textFieldsSet.toArray(new String[0]);
        
        Arrays.sort(clickable);
        Arrays.sort(textFields);
        
        return new UIStructure(appName, clickable, textFields, focusedField);
    }
    
    private void extractFromNodeRecursively(AccessibilityNodeInfo node, 
                                           Set<String> clickableSet, 
                                           Set<String> textFieldsSet) {
        if (node == null) {
            return;
        }
        
        String nodeLabel = getNodeLabel(node);
        String nodeClass = node.getClassName().toString();
        
        // Only process visible nodes
        if (node.isVisibleToUser()) {
            // Check if it's a clickable element
            if (isClickable(node) || isButton(node) || isMenuItem(node)) {
                if (nodeLabel != null && !nodeLabel.isEmpty() && !isSystemElement(nodeLabel)) {
                    clickableSet.add(nodeLabel);
                }
            }
            
            // Check if it's a text field
            if (isTextField(node)) {
                if (nodeLabel != null && !nodeLabel.isEmpty()) {
                    textFieldsSet.add(nodeLabel);
                } else {
                    textFieldsSet.add("text_field_" + textFieldsSet.size());
                }
            }
        }
        
        // Recursively process children
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                extractFromNodeRecursively(child, clickableSet, textFieldsSet);
                child.recycle();
            }
        }
    }
    
    private String getNodeLabel(AccessibilityNodeInfo node) {
        if (node == null) {
            return "";
        }
        
        CharSequence text = node.getText();
        CharSequence contentDesc = node.getContentDescription();
        CharSequence viewName = node.getViewIdResourceName();
        
        // Priority: text > contentDescription > viewId
        if (text != null && !text.toString().trim().isEmpty()) {
            return text.toString().trim();
        }
        
        if (contentDesc != null && !contentDesc.toString().trim().isEmpty()) {
            return contentDesc.toString().trim();
        }
        
        if (viewName != null) {
            return viewName.replaceAll(".*:id/", "");
        }
        
        return "";
    }
    
    private String getCurrentAppName(AccessibilityNodeInfo rootNode) {
        try {
            String packageName = rootNode.getPackageName().toString();
            if (packageName.contains(".")) {
                return packageName.substring(packageName.lastIndexOf('.') + 1);
            }
            return packageName;
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    private boolean isClickable(AccessibilityNodeInfo node) {
        return node.isClickable() || 
               node.getActionList().contains(AccessibilityNodeInfo.ACTION_CLICK);
    }
    
    private boolean isButton(AccessibilityNodeInfo node) {
        String className = node.getClassName().toString().toLowerCase();
        return className.contains("button") || 
               className.contains("imagebutton") || 
               className.contains("fab");
    }
    
    private boolean isMenuItem(AccessibilityNodeInfo node) {
        String className = node.getClassName().toString().toLowerCase();
        return className.contains("menuitem") || 
               className.contains("navigationitem");
    }
    
    private boolean isTextField(AccessibilityNodeInfo node) {
        String className = node.getClassName().toString().toLowerCase();
        boolean isEditText = className.contains("edittext") || 
                           className.contains("textview") && node.isEditable();
        boolean hasInputType = (node.getInputType() & 0x20000) != 0; // TYPE_CLASS_TEXT
        
        return isEditText || hasInputType || node.isEditable();
    }
    
    private boolean isSystemElement(String label) {
        String lowerLabel = label.toLowerCase();
        return lowerLabel.contains("android") || 
               lowerLabel.contains("system") || 
               lowerLabel.startsWith("android.") ||
               lowerLabel.startsWith("com.android.") ||
               lowerLabel.startsWith("com.google.android.");
    }
    
    /**
     * Execute a click action on a specific element
     */
    public void performClick(String targetLabel) {
        try {
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode == null) {
                Log.w(TAG, "No root node available for click");
                return;
            }
            
            boolean clicked = clickNodeRecursively(rootNode, targetLabel);
            rootNode.recycle();
            
            if (clicked) {
                Log.i(TAG, "Successfully clicked: " + targetLabel);
            } else {
                Log.w(TAG, "Failed to click: " + targetLabel);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error performing click", e);
        }
    }
    
    /**
     * Execute a type action on a specific text field
     */
    public void performType(String targetLabel, String text) {
        try {
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode == null) {
                Log.w(TAG, "No root node available for type");
                return;
            }
            
            boolean typed = typeNodeRecursively(rootNode, targetLabel, text);
            rootNode.recycle();
            
            if (typed) {
                Log.i(TAG, "Successfully typed in: " + targetLabel);
            } else {
                Log.w(TAG, "Failed to type in: " + targetLabel);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error performing type", e);
        }
    }
    
    /**
     * Perform a scroll action
     */
    public void performScroll(String direction) {
        try {
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode == null) {
                Log.w(TAG, "No root node available for scroll");
                return;
            }
            
            boolean scrolled = scrollNode(rootNode, direction);
            rootNode.recycle();
            
            if (scrolled) {
                Log.i(TAG, "Successfully scrolled: " + direction);
            } else {
                Log.w(TAG, "Failed to scroll: " + direction);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error performing scroll", e);
        }
    }
    
    private boolean clickNodeRecursively(AccessibilityNodeInfo node, String targetLabel) {
        if (node == null) {
            return false;
        }
        
        String nodeLabel = getNodeLabel(node);
        
        // Check if this node matches the target
        if (nodeLabel.equals(targetLabel) && isClickable(node)) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            return true;
        }
        
        // Check children
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                if (clickNodeRecursively(child, targetLabel)) {
                    child.recycle();
                    return true;
                }
                child.recycle();
            }
        }
        
        return false;
    }
    
    private boolean typeNodeRecursively(AccessibilityNodeInfo node, String targetLabel, String text) {
        if (node == null) {
            return false;
        }
        
        String nodeLabel = getNodeLabel(node);
        
        // Check if this node matches the target
        if (nodeLabel.equals(targetLabel) && (isTextField(node) || node.isEditable())) {
            // Clear existing text
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            node.performAction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS);
            
            // Set new text
            android.os.Bundle arguments = new android.os.Bundle();
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
            
            return true;
        }
        
        // Check children
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                if (typeNodeRecursively(child, targetLabel, text)) {
                    child.recycle();
                    return true;
                }
                child.recycle();
            }
        }
        
        return false;
    }
    
    private boolean scrollNode(AccessibilityNodeInfo node, String direction) {
        // Try to find a scrollable container
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                if (child.isScrollable()) {
                    if (direction.equals("forward")) {
                        child.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                    } else {
                        child.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
                    }
                    child.recycle();
                    return true;
                }
                child.recycle();
            }
        }
        
        // Fallback: scroll the root if scrollable
        if (node.isScrollable()) {
            if (direction.equals("forward")) {
                node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            } else {
                node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
            }
            return true;
        }
        
        return false;
    }
    
    /**
     * Perform a global back action
     */
    public void performBack() {
        try {
            performGlobalAction(GLOBAL_ACTION_BACK);
            Log.i(TAG, "Performed back action");
        } catch (Exception e) {
            Log.e(TAG, "Error performing back action", e);
        }
    }
    
    /**
     * Set callback for UI structure changes
     */
    public void setUIStructureCallback(UIStructureCallback callback) {
        this.uiCallback = callback;
    }
}