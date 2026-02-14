package com.minijarvis.app.llm;

import android.content.Context;
import android.util.Log;
import com.google.gson.Gson;
import com.minijarvis.app.model.UIStructure;
import com.minijarvis.app.model.ActionModel;
import java.util.Random;

/**
 * Mock LLM Engine for testing without the actual model
 * Uses simple rule-based responses for demonstration
 */
public class MockLLMEngine {
    private static final String TAG = "MockLLMEngine";
    private Context context;
    private Gson gson = new Gson();
    private Random random = new Random();
    
    // System prompt (same as real engine)
    private static final String SYSTEM_PROMPT = "You are MiniJarvis, an Android automation engine.\n\n" +
            "You do not chat.\n" +
            "You do not explain.\n" +
            "You only choose ONE next action.\n\n" +
            "You receive:\n" +
            "- User instruction\n" +
            "- Structured UI JSON\n\n" +
            "Return strictly valid JSON:\n" +
            "{\n" +
            "  \"action\": \"\",\n" +
            "  \"target\": \"\",\n" +
            "  \"text\": \"\"\n" +
            "}\n\n" +
            "Allowed actions:\n" +
            "- click\n" +
            "- type\n" +
            "- scroll\n" +
            "- open_app\n" +
            "- go_back\n" +
            "- nothing\n\n" +
            "Rules:\n" +
            "- target must match exactly from clickable or text_fields\n" +
            "- text only when action = type\n" +
            "- never hallucinate elements\n" +
            "- if unsure, return action = \"nothing\"\n" +
            "- output JSON only";

    public MockLLMEngine(Context context) {
        this.context = context;
    }

    public boolean initialize() {
        Log.i(TAG, "Mock LLM Engine initialized (simulation mode)");
        return true;
    }

    /**
     * Generate an action based on user instruction and UI structure
     * Uses simple rule-based logic for demonstration
     */
    public ActionModel generateAction(String userInstruction, UIStructure uiStructure) {
        Log.i(TAG, "Processing instruction: " + userInstruction);
        
        String instruction = userInstruction.toLowerCase();
        
        // Check if user wants to type something
        if (instruction.contains("type") || instruction.contains("send") || instruction.contains("write")) {
            return handleTypeAction(userInstruction, uiStructure);
        }
        
        // Check if user wants to scroll
        if (instruction.contains("scroll")) {
            return handleScrollAction(userInstruction, uiStructure);
        }
        
        // Check if user wants to go back
        if (instruction.contains("back") || instruction.contains("go back")) {
            return new ActionModel(ActionModel.ACTION_GO_BACK, "", "");
        }
        
        // Check if user wants to open an app
        if (instruction.startsWith("open ") || instruction.contains("launch")) {
            String appName = extractAppName(instruction);
            return new ActionModel(ActionModel.ACTION_OPEN_APP, appName, "");
        }
        
        // Check if user wants to click something
        if (instruction.contains("click") || instruction.contains("tap") || instruction.contains("press")) {
            return handleClickAction(userInstruction, uiStructure);
        }
        
        // Default: nothing
        Log.i(TAG, "No matching action found, returning nothing");
        return new ActionModel(ActionModel.ACTION_NOTHING, "", "");
    }

    private ActionModel handleClickAction(String instruction, UIStructure uiStructure) {
        // Extract the target from the instruction
        String[] words = instruction.split("\\s+");
        String targetToFind = null;
        
        // Try to find a word that matches a clickable element
        for (int i = 0; i < words.length; i++) {
            if (words[i].equals("click") || words[i].equals("tap") || words[i].equals("press")) {
                if (i + 1 < words.length) {
                    targetToFind = words[i + 1];
                    // Also include subsequent words if they exist
                    if (i + 2 < words.length) {
                        targetToFind += " " + words[i + 2];
                    }
                }
                break;
            }
        }
        
        // If no explicit target, look for keywords in instruction
        if (targetToFind == null) {
            for (String word : words) {
                if (word.length() > 2 && !isCommonWord(word)) {
                    targetToFind = word;
                    break;
                }
            }
        }
        
        // Search for matching clickable element
        if (uiStructure.clickable != null && targetToFind != null) {
            for (String clickable : uiStructure.clickable) {
                if (clickable.toLowerCase().contains(targetToFind.toLowerCase()) ||
                    targetToFind.toLowerCase().contains(clickable.toLowerCase())) {
                    return new ActionModel(ActionModel.ACTION_CLICK, clickable, "");
                }
            }
        }
        
        // If still no match, try first clickable element
        if (uiStructure.clickable != null && uiStructure.clickable.length > 0) {
            return new ActionModel(ActionModel.ACTION_CLICK, uiStructure.clickable[0], "");
        }
        
        return new ActionModel(ActionModel.ACTION_NOTHING, "", "");
    }

    private ActionModel handleTypeAction(String instruction, UIStructure uiStructure) {
        // Find the text to type
        String textToType = "";
        
        // Extract text after "type", "send", or "write"
        String[] triggerWords = {"type", "send", "write", "message"};
        for (String trigger : triggerWords) {
            int idx = instruction.indexOf(trigger);
            if (idx >= 0) {
                String after = instruction.substring(idx + trigger.length()).trim();
                // Remove common prefixes
                after = after.replaceFirst("^(to|in|to the)\\s+", "");
                if (!after.isEmpty()) {
                    textToType = after;
                    break;
                }
            }
        }
        
        // Find a text field
        String targetField = "";
        if (uiStructure.textFields != null && uiStructure.textFields.length > 0) {
            targetField = uiStructure.textFields[0];
        } else if (uiStructure.focused != null && !uiStructure.focused.isEmpty()) {
            targetField = uiStructure.focused;
        }
        
        if (!targetField.isEmpty() && !textToType.isEmpty()) {
            return new ActionModel(ActionModel.ACTION_TYPE, targetField, textToType);
        }
        
        return new ActionModel(ActionModel.ACTION_NOTHING, "", "");
    }

    private ActionModel handleScrollAction(String instruction, UIStructure uiStructure) {
        if (instruction.contains("down") || instruction.contains("forward")) {
            return new ActionModel(ActionModel.ACTION_SCROLL, "forward", "");
        } else if (instruction.contains("up") || instruction.contains("backward")) {
            return new ActionModel(ActionModel.ACTION_SCROLL, "backward", "");
        }
        
        // Default to scroll down
        return new ActionModel(ActionModel.ACTION_SCROLL, "forward", "");
    }

    private String extractAppName(String instruction) {
        // Extract app name from "open X" or "launch X"
        String[] words = instruction.split("\\s+");
        for (int i = 0; i < words.length; i++) {
            if (words[i].equals("open") || words[i].equals("launch")) {
                if (i + 1 < words.length) {
                    return words[i + 1];
                }
            }
        }
        return "";
    }

    private boolean isCommonWord(String word) {
        String[] commonWords = {"the", "a", "an", "to", "in", "on", "at", "for", "of", "and", "or", "but", 
                                "is", "are", "was", "were", "be", "been", "being", "have", "has", "had",
                                "do", "does", "did", "will", "would", "could", "should", "may", "might",
                                "can", "please", "try", "click", "tap", "press", "open", "close", "go"};
        for (String common : commonWords) {
            if (word.toLowerCase().equals(common)) {
                return true;
            }
        }
        return false;
    }

    public boolean isReady() {
        return true;
    }

    public void cleanup() {
        Log.i(TAG, "Mock LLM Engine cleaned up");
    }
}