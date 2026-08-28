package com.wtm.ui;

import java.awt.*;

/** Theme catalog: real FlatLaf themes plus intentional North Star/holiday overlays. */
public enum AppTheme {
    NORTH_STAR("NORTH_STAR","North Star • Silver / Black",true,true,c(5,7,9),c(17,20,24),c(43,47,54),c(107,114,128),c(209,213,219),c(150,155,165),c(13,110,253)),
    FLATLAF_LIGHT("FLATLAF_LIGHT","FlatLaf • Light",false,false,c(247,248,250),Color.WHITE,c(242,244,247),c(205,209,214),c(35,38,42),c(108,113,120),c(38,117,191)),
    FLATLAF_DARK("FLATLAF_DARK","FlatLaf • Dark",true,false,c(60,63,65),c(69,73,74),c(76,80,82),c(96,99,102),c(238,238,238),c(174,177,179),c(75,110,175)),
    FLATLAF_INTELLIJ("FLATLAF_INTELLIJ","FlatLaf • IntelliJ",false,false,c(242,242,242),Color.WHITE,c(248,248,248),c(198,198,198),c(30,30,30),c(105,105,105),c(53,116,240)),
    FLATLAF_DARCULA("FLATLAF_DARCULA","FlatLaf • Darcula",true,false,c(43,43,43),c(60,63,65),c(69,73,74),c(85,85,85),c(187,187,187),c(150,150,150),c(74,136,199)),
    ARC("ARC","FlatLaf • Arc",false,false,c(244,245,247),c(250,250,250),c(238,240,243),c(200,204,210),c(48,52,58),c(112,118,126),c(82,139,255)),
    ARC_DARK("ARC_DARK","FlatLaf • Arc Dark",true,false,c(45,48,54),c(56,60,67),c(63,67,75),c(82,87,96),c(220,223,228),c(155,161,170),c(82,139,255)),
    CARBON("CARBON","FlatLaf • Carbon",true,false,c(31,31,31),c(42,42,42),c(50,50,50),c(76,76,76),c(222,222,222),c(160,160,160),c(180,180,180)),
    COBALT_2("COBALT_2","FlatLaf • Cobalt 2",true,false,c(12,32,51),c(18,45,70),c(24,55,84),c(44,78,108),c(237,246,255),c(152,181,207),c(0,165,255)),
    DRACULA("DRACULA","FlatLaf • Dracula",true,false,c(40,42,54),c(48,50,65),c(56,58,75),c(76,78,95),c(248,248,242),c(170,170,180),c(189,147,249)),
    NORD("NORD","FlatLaf • Nord",true,false,c(46,52,64),c(59,66,82),c(67,76,94),c(76,86,106),c(236,239,244),c(174,181,194),c(136,192,208)),
    ONE_DARK("ONE_DARK","FlatLaf • One Dark",true,false,c(40,44,52),c(48,53,63),c(57,63,75),c(78,84,98),c(220,223,228),c(158,164,176),c(97,175,239)),
    SOLARIZED_LIGHT("SOLARIZED_LIGHT","FlatLaf • Solarized Light",false,false,c(253,246,227),c(238,232,213),c(250,244,226),c(210,204,185),c(88,110,117),c(101,123,131),c(38,139,210)),
    SOLARIZED_DARK("SOLARIZED_DARK","FlatLaf • Solarized Dark",true,false,c(0,43,54),c(7,54,66),c(13,63,74),c(42,83,91),c(147,161,161),c(101,123,131),c(38,139,210)),
    GRADIANTO_MIDNIGHT("GRADIANTO_MIDNIGHT","FlatLaf • Gradianto Midnight Blue",true,false,c(25,30,44),c(32,38,55),c(40,47,66),c(62,72,95),c(224,229,239),c(154,164,183),c(92,139,255)),
    GRADIANTO_NATURE("GRADIANTO_NATURE","FlatLaf • Gradianto Nature Green",true,false,c(25,38,34),c(33,49,44),c(42,59,53),c(62,85,76),c(228,239,234),c(159,183,173),c(86,181,130)),
    ARC_DARK_ORANGE("ARC_DARK_ORANGE","FlatLaf • Arc Dark Orange",true,false,c(45,48,54),c(56,60,67),c(63,67,75),c(82,87,96),c(224,226,230),c(158,164,172),c(255,152,48)),
    HIGH_CONTRAST("HIGH_CONTRAST","FlatLaf • High Contrast",true,false,Color.BLACK,c(12,12,12),c(22,22,22),c(135,135,135),Color.WHITE,c(220,220,220),c(0,190,255)),
    CHRISTMAS("CHRISTMAS","Holiday • Christmas",true,true,c(10,24,18),c(18,43,32),c(25,55,40),c(70,103,82),c(250,248,242),c(191,211,198),c(220,45,55)),
    HALLOWEEN("HALLOWEEN","Holiday • Halloween",true,true,c(20,13,25),c(37,24,45),c(48,31,57),c(92,65,104),c(248,241,252),c(196,173,207),c(245,132,31)),
    THANKSGIVING("THANKSGIVING","Holiday • Thanksgiving",true,true,c(28,18,11),c(53,34,19),c(65,42,24),c(108,74,45),c(255,246,232),c(216,186,148),c(205,105,42)),
    INDEPENDENCE("INDEPENDENCE","Holiday • Independence Day",true,true,c(7,20,44),c(15,39,73),c(22,52,92),c(59,88,128),c(248,251,255),c(177,197,224),c(220,45,55)),
    VALENTINE("VALENTINE","Holiday • Valentine’s Day",false,true,c(255,242,246),c(255,250,252),c(255,235,242),c(238,191,206),c(74,35,52),c(134,87,105),c(220,64,112)),
    ST_PATRICKS("ST_PATRICKS","Holiday • St. Patrick’s Day",true,true,c(7,29,20),c(14,53,35),c(20,66,43),c(55,104,76),c(242,252,247),c(166,207,184),c(62,193,112)),
    WINTER_FROST("WINTER_FROST","Seasonal • Winter Frost",false,true,c(235,244,250),c(248,252,255),c(226,239,248),c(186,209,226),c(31,55,72),c(91,120,140),c(55,143,205));

    private final String id,display;
    private final boolean dark,branded;
    private final Color bg,panel,panel2,border,text,muted,accent;

    AppTheme(String id,String display,boolean dark,boolean branded,Color bg,Color panel,Color panel2,Color border,Color text,Color muted,Color accent){
        this.id=id;this.display=display;this.dark=dark;this.branded=branded;
        this.bg=bg;this.panel=panel;this.panel2=panel2;this.border=border;this.text=text;this.muted=muted;this.accent=accent;
    }

    public String id(){return id;} public String display(){return display;} public boolean dark(){return dark;} public boolean branded(){return branded;}
    public Color bg(){return bg;} public Color panel(){return panel;} public Color panel2(){return panel2;} public Color border(){return border;} public Color text(){return text;} public Color muted(){return muted;} public Color accent(){return accent;}
    @Override public String toString(){return display;}

    public static AppTheme fromId(String id){
        if(id!=null){
            String normalized=id.trim();
            AppTheme migrated=switch(normalized.toUpperCase()){
                case "DARK"->FLATLAF_DARK; case "LIGHT"->FLATLAF_LIGHT; case "GRAPHITE"->CARBON;
                case "OPERATIONS_BLUE"->COBALT_2; case "MIDNIGHT"->GRADIANTO_MIDNIGHT; case "SLATE"->ARC_DARK;
                case "EMERALD"->GRADIANTO_NATURE; case "AMBER_NIGHT"->ARC_DARK_ORANGE; case "WARM_NEUTRAL"->ARC;
                default->null;
            };
            if(migrated!=null)return migrated;
            for(AppTheme t:values())if(t.id.equalsIgnoreCase(normalized)||t.display.equalsIgnoreCase(normalized))return t;
        }
        return NORTH_STAR;
    }

    private static Color c(int r,int g,int b){return new Color(r,g,b);}
}
