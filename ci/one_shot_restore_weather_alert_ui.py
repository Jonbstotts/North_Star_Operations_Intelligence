from pathlib import Path

p=Path('src/com/wtm/ui/OperationsWorkspaceFrame.java')
text=p.read_text()

def replace_once(old,new):
    global text
    count=text.count(old)
    if count != 1:
        raise SystemExit(f'expected one match, found {count}: {old[:120]!r}')
    text=text.replace(old,new,1)

replace_once(
'''    private JLabel alertBadge;
    private JButton dashboardLayoutGear;''',
'''    private JLabel alertBadge;
    private HeaderTicker headerTicker;
    private JButton dashboardLayoutGear;'''
)

replace_once(
'''        alertBadge=new JLabel("●  0 alerts");
        alertBadge.setForeground(Theme.muted());
        alertBadge.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,12));
        right.add(alertBadge);''',
'''        alertBadge=new JLabel("●  0 alerts");
        alertBadge.setForeground(Theme.muted());
        alertBadge.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,12));
        alertBadge.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        alertBadge.setToolTipText("Show active weather alerts");
        alertBadge.addMouseListener(new java.awt.event.MouseAdapter(){
            @Override public void mouseClicked(java.awt.event.MouseEvent e){
                showWeatherAlertMenu(alertBadge);
            }
        });
        right.add(alertBadge);'''
)

replace_once(
'''        HeaderTicker ticker=new HeaderTicker(config.tickerText);
        ticker.setBorder(new EmptyBorder(0,10,0,12));
        strip.add(ticker,BorderLayout.CENTER);
        return strip;''',
'''        headerTicker=new HeaderTicker();
        headerTicker.setBorder(new EmptyBorder(0,10,0,12));
        headerTicker.setEntries(headerTickerEntries());
        strip.add(headerTicker,BorderLayout.CENTER);
        return strip;'''
)

replace_once(
'''    private void toggleDashboardLayoutFromGear(){''',
'''    private void showWeatherAlertMenu(Component invoker){
        JPopupMenu menu=new JPopupMenu();
        menu.setBorder(BorderFactory.createLineBorder(Theme.border(),1));

        if(config.liveSevereWeatherMode){
            JPanel test=weatherAlertPopupRow(
                    "SEVERE WEATHER TEST MODE",
                    "Manual severe-weather mode is active for presentation testing.",
                    Theme.danger()
            );
            menu.add(test);
            if(alerts!=null&&!alerts.isEmpty())menu.addSeparator();
        }

        if(alerts==null||alerts.isEmpty()){
            if(!config.liveSevereWeatherMode){
                JMenuItem none=new JMenuItem("No active weather alerts");
                none.setEnabled(false);
                menu.add(none);
            }
        }else{
            List<WeatherAlert> ordered=alerts.stream()
                    .sorted(Comparator.comparingInt(this::weatherAlertPriority).reversed())
                    .toList();
            for(int i=0;i<ordered.size();i++){
                WeatherAlert alert=ordered.get(i);
                String title=shortAlertName(alert.event());
                String details=weatherAlertBrief(alert);
                menu.add(weatherAlertPopupRow(title,details,weatherAlertColor(alert)));
                if(i<ordered.size()-1)menu.addSeparator();
            }
        }

        menu.show(invoker,0,invoker.getHeight()+4);
    }

    private JPanel weatherAlertPopupRow(String title,String details,Color color){
        JPanel row=new JPanel();
        row.setBackground(Theme.panel());
        row.setBorder(new EmptyBorder(9,12,9,12));
        row.setLayout(new BoxLayout(row,BoxLayout.Y_AXIS));
        row.setPreferredSize(new Dimension(440,Math.max(58,details.length()>75?78:62)));

        JLabel heading=new JLabel(title);
        heading.setForeground(color);
        heading.setFont(new Font(Font.SANS_SERIF,Font.BOLD,12));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel description=new JLabel(
                "<html><div style='width:390px'>"+escapeHtml(details)+"</div></html>");
        description.setForeground(color);
        description.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,10));
        description.setAlignmentX(Component.LEFT_ALIGNMENT);

        row.add(heading);
        row.add(Box.createVerticalStrut(3));
        row.add(description);
        return row;
    }

    private List<TickerEntry> headerTickerEntries(){
        List<TickerEntry> entries=new ArrayList<>();

        if(config.liveSevereWeatherMode){
            entries.add(new TickerEntry(
                    "⚠ SEVERE WEATHER TEST MODE • Manual severe-weather presentation is active",
                    Theme.danger()
            ));
        }

        if(alerts!=null&&!alerts.isEmpty()){
            alerts.stream()
                    .sorted(Comparator.comparingInt(this::weatherAlertPriority).reversed())
                    .forEach(alert->entries.add(new TickerEntry(
                            weatherAlertTickerText(alert),
                            weatherAlertColor(alert)
                    )));
        }

        if(config.tickerText!=null&&!config.tickerText.isBlank())
            entries.add(new TickerEntry(config.tickerText.trim(),Theme.text()));

        if(entries.isEmpty())
            entries.add(new TickerEntry("North Star operations monitoring active",Theme.text()));
        return entries;
    }

    private String weatherAlertTickerText(WeatherAlert alert){
        String prefix=weatherAlertPriority(alert)>=875?"⚠ SEVERE WEATHER • ":"⚠ WEATHER ALERT • ";
        return prefix+weatherAlertBrief(alert);
    }

    private String weatherAlertBrief(WeatherAlert alert){
        if(alert==null)return "Weather alert active";
        String event=safe(alert.event()).trim();
        String headline=safe(alert.headline()).trim();
        if(headline.isBlank())return event.isBlank()?"Weather alert active":event;
        if(!event.isBlank()&&headline.toLowerCase(Locale.ROOT).contains(event.toLowerCase(Locale.ROOT)))
            return headline.length()>150?headline.substring(0,149)+"…":headline;
        String brief=event.isBlank()?headline:event+" • "+headline;
        return brief.length()>150?brief.substring(0,149)+"…":brief;
    }

    private Color weatherAlertColor(WeatherAlert alert){
        return weatherAlertPriority(alert)>=875?Theme.danger():Theme.warn();
    }

    private void refreshHeaderTicker(){
        if(headerTicker!=null)headerTicker.setEntries(headerTickerEntries());
    }

    private void toggleDashboardLayoutFromGear(){'''
)

replace_once(
'''        if(alertBadge!=null){
            alertBadge.setText("●  "+alerts.size()+" alert"+(alerts.size()==1?"":"s"));
            alertBadge.setForeground(alerts.isEmpty()?Theme.muted():Theme.warn());
        }
    }''',
'''        if(alertBadge!=null){
            if(config.liveSevereWeatherMode){
                alertBadge.setText("●  SEVERE TEST"+(alerts.isEmpty()?"":" • "+alerts.size()+" alert"+(alerts.size()==1?"":"s")));
                alertBadge.setForeground(Theme.danger());
            }else{
                alertBadge.setText("●  "+alerts.size()+" alert"+(alerts.size()==1?"":"s"));
                WeatherAlert primary=primaryWeatherAlert();
                alertBadge.setForeground(primary==null?Theme.muted():weatherAlertColor(primary));
            }
        }
        refreshHeaderTicker();
    }'''
)

replace_once(
'''    private boolean hasSevereAutomaticPriority(){
        return config.severeWeatherMapPriority
                &&alerts!=null
                &&alerts.stream().anyMatch(alert->
                        alert.severity()!=null
                        &&(
                            alert.severity().equalsIgnoreCase("Extreme")
                            ||alert.severity().equalsIgnoreCase("Severe")
                        )
                );
    }''',
'''    private boolean hasSevereAutomaticPriority(){
        if(!config.severeWeatherMapPriority)return false;
        if(config.liveSevereWeatherMode)return true;
        return alerts!=null
                &&alerts.stream().anyMatch(alert->
                        weatherAlertPriority(alert)>=875
                );
    }'''
)

replace_once(
'''    private boolean automaticSevereWeatherActive(){
        if(!config.automaticSevereWeatherMode
                ||alerts==null
                ||alerts.isEmpty())
            return false;''',
'''    private boolean automaticSevereWeatherActive(){
        if(config.liveSevereWeatherMode)return true;
        if(!config.automaticSevereWeatherMode
                ||alerts==null
                ||alerts.isEmpty())
            return false;'''
)

old='''    private static final class HeaderTicker extends JPanel {
        private final String message;
        private final javax.swing.Timer timer;
        private int offset=0;

        private HeaderTicker(String text){
            message=text.trim();
            setOpaque(false);
            setForeground(Theme.text());
            setFont(new Font(Font.SANS_SERIF,Font.BOLD,11));

            timer=new javax.swing.Timer(35,e->{
                FontMetrics metrics=getFontMetrics(getFont());
                int messageWidth=Math.max(1,metrics.stringWidth(message));
                int travel=Math.max(1,getWidth()+messageWidth+240);
                offset=(offset+1)%travel;
                repaint();
            });
            timer.setCoalesce(true);
        }

        @Override public void addNotify(){
            super.addNotify();
            timer.start();
        }

        @Override public void removeNotify(){
            timer.stop();
            super.removeNotify();
        }

        @Override protected void paintComponent(Graphics g){
            super.paintComponent(g);
            Graphics2D g2=(Graphics2D)g.create();
            try{
                g2.setRenderingHint(
                        RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(getFont());
                g2.setColor(getForeground());
                FontMetrics metrics=g2.getFontMetrics();
                int baseline=(getHeight()+metrics.getAscent()-metrics.getDescent())/2;
                int x=getWidth()+80-offset;
                g2.drawString(message,x,baseline);
            }finally{
                g2.dispose();
            }
        }
    }
'''
new='''    private record TickerEntry(String text,Color color){}

    private static final class HeaderTicker extends JPanel {
        private List<TickerEntry> entries=List.of();
        private final javax.swing.Timer timer;
        private int offset=0;

        private HeaderTicker(){
            setOpaque(false);
            setFont(new Font(Font.SANS_SERIF,Font.BOLD,11));

            timer=new javax.swing.Timer(35,e->{
                int messageWidth=Math.max(1,totalMessageWidth());
                int travel=Math.max(1,getWidth()+messageWidth+240);
                offset=(offset+1)%travel;
                repaint();
            });
            timer.setCoalesce(true);
        }

        private void setEntries(List<TickerEntry> values){
            entries=values==null?List.of():List.copyOf(values);
            offset=0;
            repaint();
        }

        private int totalMessageWidth(){
            FontMetrics metrics=getFontMetrics(getFont());
            int width=0;
            for(int i=0;i<entries.size();i++){
                TickerEntry entry=entries.get(i);
                width+=metrics.stringWidth(entry.text());
                if(i<entries.size()-1)width+=metrics.stringWidth("     •     ");
            }
            return width;
        }

        @Override public void addNotify(){
            super.addNotify();
            timer.start();
        }

        @Override public void removeNotify(){
            timer.stop();
            super.removeNotify();
        }

        @Override protected void paintComponent(Graphics g){
            super.paintComponent(g);
            Graphics2D g2=(Graphics2D)g.create();
            try{
                g2.setRenderingHint(
                        RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(getFont());
                FontMetrics metrics=g2.getFontMetrics();
                int baseline=(getHeight()+metrics.getAscent()-metrics.getDescent())/2;
                int x=getWidth()+80-offset;
                for(int i=0;i<entries.size();i++){
                    TickerEntry entry=entries.get(i);
                    g2.setColor(entry.color());
                    g2.drawString(entry.text(),x,baseline);
                    x+=metrics.stringWidth(entry.text());
                    if(i<entries.size()-1){
                        String separator="     •     ";
                        g2.setColor(Theme.muted());
                        g2.drawString(separator,x,baseline);
                        x+=metrics.stringWidth(separator);
                    }
                }
            }finally{
                g2.dispose();
            }
        }
    }
'''
replace_once(old,new)

p.write_text(text)
