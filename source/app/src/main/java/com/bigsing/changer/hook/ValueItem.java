package com.bigsing.changer.hook;

import android.widget.EditText;

/**
 */

public class ValueItem {
    public EditText edtValue;
    boolean isSupported;
    private String uiName;
    private String key;
    private String value;
    private String hardValue;
    private boolean isChange;
    private int mColorDiff;

    public ValueItem(String sKey, String sUiName, String sValue, String sHardValue, boolean isSupported, boolean isChange, int clrDiff) {
        this.key = sKey;
        this.uiName = sUiName;
        this.value = sValue;
        this.hardValue = sHardValue;
        this.isSupported = isSupported;
        this.isChange = isChange;
        this.mColorDiff = clrDiff;
    }

    public String getUiName() {
        return uiName;
    }

    public void setUiName(String uiName) {
        this.uiName = uiName;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getHardValue() {
        return hardValue;
    }

    public void setHardValue(String hardValue) {
        this.hardValue = hardValue;
    }

    public boolean isChange() {
        return isChange;
    }

    public void setChange(boolean change) {
        isChange = change;
    }

    public int getColorDiff() {
        return mColorDiff;
    }

    public void setColorDiff(int clrDiff) {
        this.mColorDiff = clrDiff;
    }


    public boolean isSupported() {
        return isSupported;
    }

    public void setSupported(boolean supported) {
        isSupported = supported;
    }
}
