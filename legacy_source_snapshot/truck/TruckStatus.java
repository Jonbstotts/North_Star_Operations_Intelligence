package com.wtm.truck;

public enum TruckStatus {
    PLANNED,
    IN_TRANSIT,
    DELAYED,
    ARRIVED,
    DELIVERED,
    CANCELLED;

    public boolean closed(){
        return this==DELIVERED||this==CANCELLED;
    }

    public static TruckStatus parse(String value){
        if(value==null||value.isBlank())return PLANNED;
        String normalized=value.trim().toUpperCase()
                .replace(' ','_')
                .replace('-','_');
        if(normalized.contains("DELIVER"))return DELIVERED;
        if(normalized.contains("CANCEL"))return CANCELLED;
        if(normalized.contains("DELAY"))return DELAYED;
        if(normalized.contains("ARRIV"))return ARRIVED;
        if(normalized.contains("TRANSIT")||normalized.contains("EN_ROUTE"))
            return IN_TRANSIT;
        try{return valueOf(normalized);}catch(Exception ex){return PLANNED;}
    }
}
