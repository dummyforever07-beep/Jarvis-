package com.minijarvis.app.model;

import com.google.gson.annotations.SerializedName;

/**
 * Action to be executed by the automation engine
 */
public class ActionModel {
    @SerializedName("action")
    public String action;
    
    @SerializedName("target")
    public String target;
    
    @SerializedName("text")
    public String text;

    public ActionModel() {
        this.action = "nothing";
        this.target = "";
        this.text = "";
    }

    public ActionModel(String action, String target, String text) {
        this.action = action;
        this.target = target;
        this.text = text;
    }

    public static final String ACTION_CLICK = "click";
    public static final String ACTION_TYPE = "type";
    public static final String ACTION_SCROLL = "scroll";
    public static final String ACTION_OPEN_APP = "open_app";
    public static final String ACTION_GO_BACK = "go_back";
    public static final String ACTION_NOTHING = "nothing";

    public boolean isValid() {
        return action != null && !action.isEmpty();
    }
}