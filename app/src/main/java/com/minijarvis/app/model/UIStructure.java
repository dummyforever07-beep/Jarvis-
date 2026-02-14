package com.minijarvis.app.model;

import com.google.gson.annotations.SerializedName;

/**
 * Represents the UI structure extracted from Accessibility API
 */
public class UIStructure {
    @SerializedName("app")
    public String app;
    
    @SerializedName("clickable")
    public String[] clickable;
    
    @SerializedName("text_fields")
    public String[] textFields;
    
    @SerializedName("focused")
    public String focused;

    public UIStructure(String app, String[] clickable, String[] textFields, String focused) {
        this.app = app;
        this.clickable = clickable;
        this.textFields = textFields;
        this.focused = focused;
    }
}