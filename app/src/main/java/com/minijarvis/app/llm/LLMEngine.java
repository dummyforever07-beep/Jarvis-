package com.minijarvis.app.llm;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.minijarvis.app.model.UIStructure;
import com.minijarvis.app.model.ActionModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * LLM Engine using llama.cpp native library
 * Loads Gemma 2B model and performs inference
 */
public class LLMEngine {
    private static final String TAG = "LLMEngine";
    private static final String MODEL_FILE = "gemma-2b-q4.gguf";
    private static final String MODEL_DIR = "models";
    private static final String MODEL_URL = "https://huggingface.co/leliuga/ggml-gemma-2b-v1-q4_0/resolve/main/gemma-2b-v1-q4_0.gguf";
    private static final int CONTEXT_SIZE = 1024;
    private static final float TEMPERATURE = 0.2f;
    private static final int MAX_TOKENS = 120;
    private static final long MIN_MODEL_SIZE = 1500000000; // Minimum 1.5GB for valid model

    private Context context;
    private long modelPtr = 0;
    private boolean initialized = false;
    private Gson gson = new Gson();

    // System prompt for MiniJarvis
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

    public LLMEngine(Context context) {
        this.context = context;
    }

    /**
     * Initialize the LLM engine by loading the model
     */
    public boolean initialize() {
        try {
            // Check if model exists in models subdirectory
            File modelDir = new File(context.getFilesDir(), MODEL_DIR);
            File modelFile = new File(modelDir, MODEL_FILE);
            
            if (!modelFile.exists()) {
                Log.i(TAG, "Model not found at: " + modelFile.getAbsolutePath());
                // Model will be downloaded by ModelDownloadActivity
                return false;
            }

            String modelPath = modelFile.getAbsolutePath();
            
            // Validate model file
            if (!modelFile.exists() || modelFile.length() < MIN_MODEL_SIZE) {
                Log.w(TAG, "Model file invalid or incomplete. Size: " + modelFile.length());
                // Delete corrupted file
                modelFile.delete();
                return false;
            }

            Log.i(TAG, "Loading model from: " + modelPath);
            Log.i(TAG, "Model size: " + (modelFile.length() / (1024 * 1024 * 1024)) + " GB");
            
            // Initialize native library
            modelPtr = nativeInit(modelPath, CONTEXT_SIZE, TEMPERATURE, MAX_TOKENS);
            if (modelPtr == 0) {
                Log.e(TAG, "Failed to initialize model - native init returned 0");
                // Model might be corrupted, delete it
                modelFile.delete();
                return false;
            }

            initialized = true;
            Log.i(TAG, "LLM Engine initialized successfully");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error initializing LLM engine", e);
            return false;
        }
    }

    /**
     * Generate an action based on user instruction and UI structure
     */
    public ActionModel generateAction(String userInstruction, UIStructure uiStructure) {
        if (!initialized || modelPtr == 0) {
            Log.w(TAG, "LLM engine not initialized");
            return new ActionModel(ActionModel.ACTION_NOTHING, "", "");
        }

        try {
            // Build prompt
            String uiJson = gson.toJson(uiStructure);
            String prompt = buildPrompt(userInstruction, uiJson);
            
            // Generate completion
            String response = nativeGenerate(modelPtr, prompt);
            if (response == null || response.isEmpty()) {
                Log.w(TAG, "Empty response from LLM");
                return new ActionModel(ActionModel.ACTION_NOTHING, "", "");
            }

            // Parse JSON response
            ActionModel action = parseActionResponse(response);
            Log.i(TAG, "Generated action: " + action.action + " target: " + action.target);
            return action;
        } catch (Exception e) {
            Log.e(TAG, "Error generating action", e);
            return new ActionModel(ActionModel.ACTION_NOTHING, "", "");
        }
    }

    /**
     * Check if model is loaded and ready
     */
    public boolean isReady() {
        return initialized && modelPtr != 0;
    }

    /**
     * Check if model file exists and is valid
     */
    public boolean isModelDownloaded() {
        File modelDir = new File(context.getFilesDir(), MODEL_DIR);
        File modelFile = new File(modelDir, MODEL_FILE);
        return modelFile.exists() && modelFile.length() >= MIN_MODEL_SIZE;
    }
    
    /**
     * Get the model file path
     */
    public String getModelPath() {
        File modelDir = new File(context.getFilesDir(), MODEL_DIR);
        File modelFile = new File(modelDir, MODEL_FILE);
        return modelFile.getAbsolutePath();
    }
    
    /**
     * Delete corrupted model file to force re-download
     */
    public boolean deleteModelFile() {
        File modelDir = new File(context.getFilesDir(), MODEL_DIR);
        File modelFile = new File(modelDir, MODEL_FILE);
        if (modelFile.exists()) {
            boolean deleted = modelFile.delete();
            Log.i(TAG, "Model file deleted: " + deleted);
            return deleted;
        }
        return true;
    }

    /**
     * Get model download URL for user
     */
    public String getModelDownloadUrl() {
        return MODEL_URL;
    }

    /**
     * Cleanup resources
     */
    public void cleanup() {
        if (modelPtr != 0) {
            nativeCleanup(modelPtr);
            modelPtr = 0;
        }
        initialized = false;
    }

    private String buildPrompt(String userInstruction, String uiJson) {
        return SYSTEM_PROMPT + "\n\n" +
                "User instruction: " + userInstruction + "\n\n" +
                "UI structure:\n" + uiJson + "\n\n" +
                "Return JSON only:";
    }

    private ActionModel parseActionResponse(String response) {
        try {
            // Clean response - extract JSON
            String jsonStr = extractJsonFromResponse(response);
            if (jsonStr == null) {
                Log.w(TAG, "No valid JSON found in response");
                return new ActionModel(ActionModel.ACTION_NOTHING, "", "");
            }
            
            return gson.fromJson(jsonStr, ActionModel.class);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing action response", e);
            return new ActionModel(ActionModel.ACTION_NOTHING, "", "");
        }
    }

    private String extractJsonFromResponse(String response) {
        // Find first { and last }
        int startIdx = response.indexOf('{');
        int endIdx = response.lastIndexOf('}');
        
        if (startIdx >= 0 && endIdx > startIdx) {
            return response.substring(startIdx, endIdx + 1);
        }
        return null;
    }

    // Native methods
    private native long nativeInit(String modelPath, int contextSize, float temperature, int maxTokens);
    private native String nativeGenerate(long modelPtr, String prompt);
    private native void nativeCleanup(long modelPtr);
}