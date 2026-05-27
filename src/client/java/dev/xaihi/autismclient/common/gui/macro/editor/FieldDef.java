package dev.xaihi.autismclient.common.gui.macro.editor;

import java.util.Collections;
import java.util.List;

public final class FieldDef {
    public final String key;
    public final String label;
    public final FieldType type;
    public final int min;
    public final int max;
    public final double decMin;
    public final double decMax;
    public final List<String> enumOptions;
    public final String showWhenKey;
    public final boolean showWhenInverted;
    public final String showWhenValue;
    public final String addLabel;
    public final String[] xyzKeys;
    public final boolean xyzDouble;
    public final CaptureMode captureMode;
    public final String[] mutuallyExclusiveWith;

    FieldDef(String key, String label, FieldType type,
             int min, int max, double decMin, double decMax,
             List<String> enumOptions,
             String showWhenKey, boolean showWhenInverted, String showWhenValue,
             String addLabel, String[] xyzKeys, boolean xyzDouble,
             CaptureMode captureMode, String[] mutuallyExclusiveWith) {
        this.key = key;
        this.label = label;
        this.type = type;
        this.min = min;
        this.max = max;
        this.decMin = decMin;
        this.decMax = decMax;
        this.enumOptions = enumOptions != null ? Collections.unmodifiableList(enumOptions) : Collections.emptyList();
        this.showWhenKey = showWhenKey;
        this.showWhenInverted = showWhenInverted;
        this.showWhenValue = showWhenValue;
        this.addLabel = addLabel != null ? addLabel : "Add";
        this.xyzKeys = xyzKeys != null ? xyzKeys.clone() : new String[]{"x", "y", "z"};
        this.xyzDouble = xyzDouble;
        this.captureMode = captureMode != null ? captureMode : CaptureMode.NONE;
        this.mutuallyExclusiveWith = mutuallyExclusiveWith != null ? mutuallyExclusiveWith.clone() : new String[0];
    }

    public String key()              { return key; }
    public String label()            { return label; }
    public FieldType type()          { return type; }
    public int min()                 { return min; }
    public int max()                 { return max; }
    public double decMin()           { return decMin; }
    public double decMax()           { return decMax; }
    public List<String> enumOptions(){ return enumOptions; }
    public String showWhenKey()      { return showWhenKey; }
    public boolean showWhenInverted(){ return showWhenInverted; }
    public String showWhenValue()    { return showWhenValue; }
    public String addLabel()         { return addLabel; }
    public String[] xyzKeys()        { return xyzKeys.clone(); }
    public boolean xyzDouble()       { return xyzDouble; }
    public boolean hasShowWhen()     { return showWhenKey != null && !showWhenKey.isEmpty(); }
    public CaptureMode captureMode()          { return captureMode; }
    public String[] mutuallyExclusiveWith()   { return mutuallyExclusiveWith.clone(); }
    public boolean hasMutualExclusion()       { return mutuallyExclusiveWith.length > 0; }
}
