package com.wtm.app;

import com.wtm.config.*;
import com.wtm.security.*;
import com.wtm.ui.*;
import com.wtm.employee.EmployeeService;
import com.wtm.callin.CallInServerManager;

import javax.swing.*;
import java.awt.*;

/** North Star Operations Intelligence desktop application entry point. */
public final class Main {
    private Main(){}

    public static void main(String[] args){
        System.setProperty("apple.awt.application.name","North Star Operations");
        System.setProperty(
                "apple.awt.application.appearance",
                "system"
        );

        SwingUtilities.invokeLater(()->{
            configureDesktopLookAndFeel();

            AppTheme bootTheme=AppTheme.NORTH_STAR;
            Theme.setActive(bootTheme.id());

            ApplicationBrand.applyApplicationIcon();

            NorthStarSplashScreen splash=new NorthStarSplashScreen();
            splash.setVisible(true);
            splash.updateProgress(18,"Loading secure application configuration...");

            /*
             * Configuration loading performs local file I/O, so keep it off the
             * Swing event thread while the branded splash remains responsive.
             */
            SwingWorker<AppConfig,Void> loader=new SwingWorker<>(){
                @Override
                protected AppConfig doInBackground(){
                    splash.updateProgress(
                            42,
                            "Initializing data providers and local storage..."
                    );
                    return ConfigService.load();
                }

                @Override
                protected void done(){
                    try{
                        AppConfig config=get();

                        /*
                         * Employee Operations is now the authoritative source
                         * for recognition data. Existing installations migrate
                         * their former Team Celebrations rows once, then the
                         * compatibility projection is refreshed on startup.
                         */
                        boolean employeeMigration=
                                EmployeeService.migrateLegacyCelebrationsIfNeeded(config);
                        EmployeeService.syncCelebrations(
                                config,
                                EmployeeService.loadForSystem()
                        );
                        if(employeeMigration)
                            ConfigService.save(config);

                        AppTheme theme=HolidayThemeService.effectiveTheme(
                                config,
                                java.time.LocalDate.now()
                        );
                        config.darkMode=theme.dark();
                        Theme.setActive(theme.id());

                        /*
                         * The optional Twilio receiver is lifecycle-managed and
                         * starts only when production webhook mode is enabled.
                         */
                        CallInServerManager.apply(config);

                        splash.updateProgress(
                                72,
                                "Preparing secure user session..."
                        );

                        /*
                         * v3.1+ is backward-compatible with the former shared
                         * administrator password. Migration occurs only once.
                         */
                        if(!UserService.hasUsers()){
                            splash.updateProgress(
                                    88,
                                    "Administrator setup required..."
                            );
                            splash.closeSplash();

                            UserAccount initial=AuthService.hasPassword()
                                    ?LegacyAdminMigrationDialog.migrate(
                                            null,
                                            AppTheme.NORTH_STAR
                                    )
                                    :FirstAdminDialog.create(
                                            null,
                                            AppTheme.NORTH_STAR
                                    );

                            if(initial==null)return;
                            SessionManager.login(initial);
                        }else{
                            splash.updateProgress(
                                    100,
                                    "North Star ready."
                            );

                            Timer closeTimer=new Timer(650,e->{
                                ((Timer)e.getSource()).stop();
                                splash.closeSplash();
                                continueStartup(config,theme);
                            });
                            closeTimer.setRepeats(false);
                            closeTimer.start();
                            return;
                        }

                        continueStartup(config,theme);
                    }catch(Exception ex){
                        splash.closeSplash();
                        ThemedDialogs.message(
                                null,
                                "North Star could not complete startup. "
                                +"Review the local configuration and try again.",
                                "Startup Error",
                                ThemedDialogs.Kind.ERROR
                        );
                    }
                }
            };
            loader.execute();
        });
    }

    private static void continueStartup(AppConfig config,AppTheme theme){
        if(config.loginRequiredOnStartup
                &&!SessionManager.isAuthenticated()){
            UserAccount account=UserLoginDialog.authenticate(
                    null,
                    "North Star Login",
                    "Sign in to continue to the operations dashboard.",
                    theme,
                    ""
            );
            if(account==null)return;
            SessionManager.login(account);
        }

        OperationsWorkspaceFrame frame=new OperationsWorkspaceFrame(config);
        frame.setVisible(true);
    }

    private static void configureDesktopLookAndFeel(){
        try{
            UIManager.setLookAndFeel(
                    UIManager.getSystemLookAndFeelClassName()
            );
        }catch(Exception ignored){}

        Font base=new Font(Font.SANS_SERIF,Font.PLAIN,14);
        for(Object key:UIManager.getDefaults().keySet()){
            if(key.toString().toLowerCase().endsWith(".font"))
                UIManager.put(key,base);
        }
    }
}
