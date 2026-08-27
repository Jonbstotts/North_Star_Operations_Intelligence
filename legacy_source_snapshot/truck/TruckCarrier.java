package com.wtm.truck;

public enum TruckCarrier {
    FEDEX("FedEx Freight"),
    STARHUB("Mercedes StarHub / Penske"),
    OTHER("Other");

    private final String display;

    TruckCarrier(String display){ this.display=display; }

    public String display(){ return display; }

    @Override public String toString(){ return display; }

    public static TruckCarrier parse(String value){
        if(value==null)return OTHER;
        String v=value.trim().toUpperCase();
        if(v.contains("FEDEX"))return FEDEX;
        if(v.contains("STAR")||v.contains("PENSKE"))return STARHUB;
        try{return valueOf(v);}catch(Exception ex){return OTHER;}
    }
}
