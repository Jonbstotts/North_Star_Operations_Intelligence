package com.wtm.model;

/**
 * Provider-neutral geocoding search result.
 *
 * The UI depends on this model rather than a specific geocoder, which makes it
 * straightforward to add another location provider later.
 */
public record LocationSearchResult(
        String name,
        double latitude,
        double longitude,
        String admin1,
        String country,
        String timezone,
        long population,
        String provider
) {
    public String displayName(){
        StringBuilder b=new StringBuilder(name==null?"":name);
        if(admin1!=null&&!admin1.isBlank()) b.append(", ").append(admin1);
        if(country!=null&&!country.isBlank()) b.append(", ").append(country);
        return b.toString();
    }

    @Override public String toString(){ return displayName(); }
}
