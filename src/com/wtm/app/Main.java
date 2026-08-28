package com.wtm.app;

import com.wtm.config.*;
import com.wtm.security.*;
import com.wtm.ui.*;
import com.wtm.employee.EmployeeService;
import com.wtm.callin.CallInServerManager;
import javax.swing.*;

/** North Star Operations Intelligence desktop application entry point. */
public final class Main {
    private Main(){}
    public static void main(String[] args){
        System.setProperty("apple.awt.application.name","North Star Operations");
        System.setProperty("apple.awt.application.appearance","system");
        SwingUtilities.invokeLater(()->{
            AppTheme bootTheme=AppTheme.fromId(ConfigService.peekThemeId());
            Theme.setActive(bootTheme.id());ApplicationBrand.applyApplicationIcon();
            String bootExperience=ConfigService.peekStartupExperience();
            NorthStarSplashScreen splash="STATIC_SPLASH".equals(bootExperience)?new NorthStarSplashScreen():null;
            if(splash!=null){splash.setVisible(true);splash.updateProgress(18,"Loading secure application configuration...");}
            SwingWorker<AppConfig,Void> loader=new SwingWorker<>(){
                @Override protected AppConfig doInBackground(){
                    if(splash!=null)splash.updateProgress(42,"Initializing data providers and local storage...");
                    return ConfigService.load();
                }
                @Override protected void done(){
                    try{
                        AppConfig config=get();prepareConfiguration(config);
                        AppTheme theme=HolidayThemeService.effectiveTheme(config,java.time.LocalDate.now());
                        config.darkMode=theme.dark();Theme.setActive(theme.id());CallInServerManager.apply(config);
                        if(splash!=null)splash.updateProgress(72,"Preparing secure user session...");
                        Runnable next=()->continueAfterStartupPresentation(config,theme);
                        if("INTRO_VIDEO".equalsIgnoreCase(config.startupExperience)){
                            if(splash!=null)splash.closeSplash();
                            if(!StartupExperienceManager.playIntroIfConfigured(config,next))next.run();
                            return;
                        }
                        if(splash!=null){
                            splash.updateProgress(100,"North Star ready.");
                            Timer timer=new Timer(650,e->{((Timer)e.getSource()).stop();splash.closeSplash();next.run();});
                            timer.setRepeats(false);timer.start();return;
                        }
                        next.run();
                    }catch(Exception ex){
                        if(splash!=null)splash.closeSplash();
                        ThemedDialogs.message(null,"North Star could not complete startup. Review the local configuration and try again.",
                                "Startup Error",ThemedDialogs.Kind.ERROR);
                    }
                }
            };loader.execute();
        });
    }

    private static void prepareConfiguration(AppConfig config){
        boolean migrated=EmployeeService.migrateLegacyCelebrationsIfNeeded(config);
        EmployeeService.syncCelebrations(config,EmployeeService.loadForSystem());
        if(migrated)ConfigService.save(config);
    }
    private static void continueAfterStartupPresentation(AppConfig config,AppTheme theme){
        if(!UserService.hasUsers()){
            UserAccount initial=AuthService.hasPassword()?LegacyAdminMigrationDialog.migrate(null,theme):FirstAdminDialog.create(null,theme);
            if(initial==null)return;SessionManager.login(initial);
        }
        continueStartup(config,theme);
    }
    private static void continueStartup(AppConfig config,AppTheme theme){
        if(config.loginRequiredOnStartup&&!SessionManager.isAuthenticated()){
            UserAccount account=UserLoginDialog.authenticate(null,"North Star Login",
                    "Sign in to continue to the operations dashboard.",theme,"");
            if(account==null)return;SessionManager.login(account);
        }
        OperationsWorkspaceFrame frame=new OperationsWorkspaceFrame(config);frame.setVisible(true);
    }
}
