package com.wtm.model;

/** Operational status used by the site's Operations Calendar. */
public enum OperationType {
    FULL_CLOSURE("Full Closure"),
    LIMITED_SERVICE("Limited Service"),
    MODIFIED_HOURS("Modified Hours");

    private final String display;

    OperationType(String display){
        this.display=display;
    }

    public String display(){return display;}

    @Override public String toString(){return display;}

    public static OperationType from(String value){
        if(value==null)return MODIFIED_HOURS;
        String normalized=value.trim()
                .toUpperCase()
                .replace(' ','_')
                .replace('-','_');

        for(OperationType type:values()){
            if(type.name().equals(normalized)
                    || type.display.toUpperCase().replace(' ','_').equals(normalized))
                return type;
        }
        return MODIFIED_HOURS;
    }
}
