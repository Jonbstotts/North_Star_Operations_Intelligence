from pathlib import Path

path = Path("src/com/wtm/ui/OperationsWorkspaceFrame.java")
source = path.read_text(encoding="utf-8")

old_event = '''    private Icon eventTagIcon(OperationEvent event,int size){
        Icon supplied=NorthStarDashboardGlyphs.icon(eventDashboardGlyphKey(event),size);
        if(supplied!=null)return supplied;
        String assetKey=eventGlyphAssetKey(event);
        Icon approved=WorkspaceGlyphs.icon(assetKey,size,Theme.text());
        if(approved!=null)return approved;

        BufferedImage image=new BufferedImage(
                size,size,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();
'''
new_event = '''    private Icon eventTagIcon(OperationEvent event,int size){
        Icon supplied=NorthStarDashboardGlyphs.icon(eventDashboardGlyphKey(event),size);
        if(supplied!=null)return supplied;
        return NorthStarDashboardGlyphs.icon("special_event",size);
    }

    /* Legacy vector event glyph painting is retained below only for historical
     * source compatibility; dashboard Events no longer route through it. */
    private Icon legacyEventTagIcon(OperationEvent event,int size){
        BufferedImage image=new BufferedImage(
                size,size,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();
'''
if old_event not in source:
    raise SystemExit("event glyph renderer anchor not found")
source = source.replace(old_event, new_event, 1)

old_celebration = '''        Icon supplied=NorthStarDashboardGlyphs.icon(dashboardKey,size);
        if(supplied!=null)return supplied;
        String assetKey=type.contains("anniversary")
                ?"confetti"
                :type.contains("employee of the month")
                    ?"toast"
                    :"birthday";
        Icon approved=WorkspaceGlyphs.icon(assetKey,size,Theme.text());
        if(approved!=null)return approved;

        /*
         * Birthday currently uses the built-in gift glyph. Paint it as vector
'''
new_celebration = '''        Icon supplied=NorthStarDashboardGlyphs.icon(dashboardKey,size);
        if(supplied!=null)return supplied;
        return NorthStarDashboardGlyphs.icon("lets_celebrate",size);
    }

    private Icon legacyCelebrationTagIcon(
            UpcomingCelebration item,
            int size
    ){
        String type=item==null||item.type()==null
                ?""
                :item.type().toLowerCase(Locale.ROOT);
        /*
         * Legacy vector recognition artwork remains isolated here for source
         * history only; Team Celebrations no longer invokes it.
         * Birthday currently uses the built-in gift glyph. Paint it as vector
'''
if old_celebration not in source:
    raise SystemExit("celebration glyph renderer anchor not found")
source = source.replace(old_celebration, new_celebration, 1)

path.write_text(source, encoding="utf-8")
