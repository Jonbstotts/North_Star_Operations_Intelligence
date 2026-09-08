package com.wtm.app;

import com.wtm.config.*;
import com.wtm.security.*;
import com.wtm.ui.*;
import com.wtm.employee.EmployeeService;
import com.wtm.callin.CallInServerManager;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/** North Star Operations Intelligence desktop application entry point. */
public final class Main {
    private Main(){}

    public static void main(String[] args){
        System.setProperty("apple.awt.application.name","North Star Operations");
        System.setProperty("apple.awt.application.appearance","system");
        SwingUtilities.invokeLater(()->{
            /* Startup identity is always NorthStar branded. The user's saved
             * theme is resolved during configuration load but is installed only
             * after authentication, immediately before the workspace is built. */
            Theme.setActive(AppTheme.NORTH_STAR.id());
            ApplicationBrand.applyApplicationIcon();

            String bootExperience=ConfigService.peekStartupExperience();
            NorthStarSplashScreen splash="STATIC_SPLASH".equals(bootExperience)
                    ?new NorthStarSplashScreen():null;
            if(splash!=null){
                splash.setVisible(true);
                splash.updateProgress(18,"Loading secure application configuration...");
            }

            SwingWorker<AppConfig,Void> loader=new SwingWorker<>(){
                @Override protected AppConfig doInBackground(){
                    if(splash!=null)
                        splash.updateProgress(42,"Initializing data providers and local storage...");
                    AppConfig loaded=ConfigService.load();
                    if(splash!=null)
                        splash.updateProgress(58,"Preparing startup identity...");
                    StartupExperienceManager.preparePoster(loaded);
                    return loaded;
                }

                @Override protected void done(){
                    try{
                        AppConfig config=get();
                        prepareConfiguration(config);
                        AppTheme workspaceTheme=HolidayThemeService.effectiveTheme(
                                config,java.time.LocalDate.now());
                        config.darkMode=workspaceTheme.dark();
                        CallInServerManager.apply(config);
                        if(splash!=null)
                            splash.updateProgress(72,"Preparing secure user session...");

                        if("INTRO_VIDEO".equalsIgnoreCase(config.startupExperience)){
                            if(splash!=null)splash.closeSplash();
                            boolean started=StartupExperienceManager.playIntroIfConfigured(
                                    config,
                                    result->continueAfterStartupPresentation(
                                            config,
                                            workspaceTheme,
                                            result.exit()==StartupExperienceManager.Exit.COMPLETED,
                                            result.poster(),
                                            result.loginBounds(),
                                            result.handoffWindow()
                                    )
                            );
                            if(!started)
                                continueAfterStartupPresentation(
                                        config,workspaceTheme,false,
                                        StartupExperienceManager.preparedPoster(config),null,null);
                            return;
                        }

                        Runnable next=()->continueAfterStartupPresentation(
                                config,workspaceTheme,false,
                                StartupExperienceManager.preparedPoster(config),null,null);
                        if(splash!=null){
                            splash.updateProgress(100,"North Star ready.");
                            Timer timer=new Timer(650,e->{
                                ((Timer)e.getSource()).stop();
                                splash.closeSplash();
                                next.run();
                            });
                            timer.setRepeats(false);
                            timer.start();
                            return;
                        }
                        next.run();
                    }catch(Exception ex){
                        if(splash!=null)splash.closeSplash();
                        ThemedDialogs.message(
                                null,
                                "North Star could not complete startup. Review the local configuration and try again.",
                                "Startup Error",
                                ThemedDialogs.Kind.ERROR
                        );
                    }
                }
            };
            loader.execute();
        });
    }

    private static void prepareConfiguration(AppConfig config){
        boolean migrated=EmployeeService.migrateLegacyCelebrationsIfNeeded(config);
        EmployeeService.syncCelebrations(config,EmployeeService.loadForSystem());
        if(migrated)ConfigService.save(config);
    }

    private static void continueAfterStartupPresentation(
            AppConfig config,
            AppTheme workspaceTheme,
            boolean animateStartupLogin,
            BufferedImage startupPoster,
            Rectangle loginBounds,
            Window startupHandoff
    ){
        if(!UserService.hasUsers()){
            StartupExperienceManager.releaseHandoff(startupHandoff);
            UserAccount initial=AuthService.hasPassword()
                    ?LegacyAdminMigrationDialog.migrate(null,AppTheme.NORTH_STAR)
                    :FirstAdminDialog.create(null,AppTheme.NORTH_STAR);
            if(initial==null)return;
            SessionManager.login(initial);
            startupHandoff=null;
        }
        continueStartup(
                config,workspaceTheme,animateStartupLogin,
                startupPoster,loginBounds,startupHandoff);
    }

    private static void continueStartup(
            AppConfig config,
            AppTheme workspaceTheme,
            boolean animateStartupLogin,
            BufferedImage startupPoster,
            Rectangle loginBounds,
            Window startupHandoff
    ){
        if(config.loginRequiredOnStartup&&!SessionManager.isAuthenticated()){
            UserAccount account=StartupLoginDialog.authenticate(
                    null,
                    "Sign in to continue to the operations dashboard.",
                    AppTheme.NORTH_STAR,
                    "",
                    startupPoster,
                    loginBounds,
                    animateStartupLogin,
                    startupHandoff
            );
            if(account==null)return;
            SessionManager.login(account);
            startupHandoff=null;
        }

        StartupExperienceManager.releaseHandoff(startupHandoff);
        Theme.setActive(workspaceTheme.id());
        OperationsWorkspaceFrame frame=new OperationsWorkspaceFrame(config);
        frame.setVisible(true);
    }
}
