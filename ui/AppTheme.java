package com.wtm.ui;

import java.awt.*;

/**
 * Complete visual themes for both the dashboard and Settings interfaces.
 */
public enum AppTheme {
    NORTH_STAR("NORTH_STAR","North Star • Silver / Black",true,
            c(5,7,9),c(17,20,24),c(43,47,54),c(107,114,128),
            c(209,213,219),c(107,114,128),c(13,110,253)),

    DARK("DARK","Dark",true,
            c(11,16,22),c(22,29,37),c(27,35,44),c(48,59,71),
            c(242,245,248),c(168,178,188),c(62,154,255)),

    LIGHT("LIGHT","Light",false,
            c(244,247,250),Color.WHITE,c(249,251,253),c(220,226,232),
            c(28,35,43),c(92,104,116),c(40,118,210)),

    GRAPHITE("GRAPHITE","Graphite / Silver",true,
            c(18,19,21),c(31,33,36),c(39,41,44),c(80,84,89),
            c(242,243,244),c(184,187,191),c(176,183,190)),

    OPERATIONS_BLUE("OPERATIONS_BLUE","Operations Blue",true,
            c(8,20,35),c(15,34,55),c(20,43,68),c(45,73,101),
            c(239,247,255),c(158,184,208),c(42,156,255)),

    MIDNIGHT("MIDNIGHT","Midnight Blue",true,
            c(8,12,28),c(17,24,47),c(23,31,59),c(53,65,101),
            c(242,245,255),c(166,176,210),c(100,131,255)),

    SLATE("SLATE","Slate",true,
            c(20,25,29),c(34,42,47),c(42,51,57),c(73,87,95),
            c(239,244,246),c(171,184,190),c(79,169,188)),

    EMERALD("EMERALD","Emerald",true,
            c(8,24,21),c(15,43,36),c(20,53,44),c(43,83,70),
            c(238,249,244),c(160,196,182),c(45,190,135)),

    AMBER_NIGHT("AMBER_NIGHT","Amber / Night",true,
            c(24,18,8),c(43,32,13),c(53,40,17),c(91,69,31),
            c(255,247,229),c(211,188,142),c(240,166,40)),

    HIGH_CONTRAST("HIGH_CONTRAST","High Contrast",true,
            Color.BLACK,c(12,12,12),c(22,22,22),c(135,135,135),
            Color.WHITE,c(220,220,220),c(0,190,255)),

    WARM_NEUTRAL("WARM_NEUTRAL","Warm Neutral",false,
            c(242,239,233),c(252,250,246),c(247,243,237),c(211,203,193),
            c(48,43,38),c(112,102,92),c(154,103,64)),

    CHRISTMAS("CHRISTMAS","Holiday • Christmas",true,
            c(10,24,18),c(18,43,32),c(25,55,40),c(70,103,82),
            c(250,248,242),c(191,211,198),c(220,45,55)),

    HALLOWEEN("HALLOWEEN","Holiday • Halloween",true,
            c(20,13,25),c(37,24,45),c(48,31,57),c(92,65,104),
            c(248,241,252),c(196,173,207),c(245,132,31)),

    THANKSGIVING("THANKSGIVING","Holiday • Thanksgiving",true,
            c(28,18,11),c(53,34,19),c(65,42,24),c(108,74,45),
            c(255,246,232),c(216,186,148),c(205,105,42)),

    INDEPENDENCE("INDEPENDENCE","Holiday • Independence Day",true,
            c(7,20,44),c(15,39,73),c(22,52,92),c(59,88,128),
            c(248,251,255),c(177,197,224),c(220,45,55)),

    VALENTINE("VALENTINE","Holiday • Valentine’s Day",false,
            c(255,242,246),c(255,250,252),c(255,235,242),c(238,191,206),
            c(74,35,52),c(134,87,105),c(220,64,112)),

    ST_PATRICKS("ST_PATRICKS","Holiday • St. Patrick’s Day",true,
            c(7,29,20),c(14,53,35),c(20,66,43),c(55,104,76),
            c(242,252,247),c(166,207,184),c(62,193,112)),

    WINTER_FROST("WINTER_FROST","Seasonal • Winter Frost",false,
            c(235,244,250),c(248,252,255),c(226,239,248),c(186,209,226),
            c(31,55,72),c(91,120,140),c(55,143,205));

    private final String id;
    private final String display;
    private final boolean dark;
    private final Color bg,panel,panel2,border,text,muted,accent;

    AppTheme(String id,String display,boolean dark,
             Color bg,Color panel,Color panel2,Color border,
             Color text,Color muted,Color accent){
        this.id=id;this.display=display;this.dark=dark;
        this.bg=bg;this.panel=panel;this.panel2=panel2;this.border=border;
        this.text=text;this.muted=muted;this.accent=accent;
    }

    public String id(){return id;}
    public String display(){return display;}
    public boolean dark(){return dark;}
    public Color bg(){return bg;}
    public Color panel(){return panel;}
    public Color panel2(){return panel2;}
    public Color border(){return border;}
    public Color text(){return text;}
    public Color muted(){return muted;}
    public Color accent(){return accent;}

    @Override public String toString(){return display;}

    public static AppTheme fromId(String id){
        if(id!=null){
            for(AppTheme t:values()){
                if(t.id.equalsIgnoreCase(id)||t.display.equalsIgnoreCase(id))
                    return t;
            }
        }
        return NORTH_STAR;
    }

    private static Color c(int r,int g,int b){return new Color(r,g,b);}
}
