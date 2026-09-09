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
            /* Authentication has a fixed North Star identity. The user's saved
             * workspace theme is resolved from configuration, but is installed
             * only after authentication succeeds. */
            Theme.setActive(AppTheme.NORTH_STAR.id());
            ApplicationBrand.applyApplicationIcon();

            SwingWorker<AppConfig,Void> loader=new SwingWorker<>(){
                @Override protected AppConfig doInBackground(){
                    return ConfigService.load();
                }

                @Override protected void done(){
                    try{
                        AppConfig config=get();
                        prepareConfiguration(config);
                        AppTheme workspaceTheme=HolidayThemeService.effectiveTheme(
                                config,java.time.LocalDate.now());
                        config.darkMode=workspaceTheme.dark();
                        CallInServerManager.apply(config);
                        continueAfterConfiguration(config,workspaceTheme);
                    }catch(Exception ex){
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

    private static void continueAfterConfiguration(
            AppConfig config,
            AppTheme workspaceTheme
    ){
        if(!UserService.hasUsers()){
            UserAccount initial=AuthService.hasPassword()
                    ?LegacyAdminMigrationDialog.migrate(null,AppTheme.NORTH_STAR)
                    :FirstAdminDialog.create(null,AppTheme.NORTH_STAR);
            if(initial==null)return;
            SessionManager.login(initial);
        }

        if(config.loginRequiredOnStartup&&!SessionManager.isAuthenticated()){
            UserAccount account=StartupLoginDialog.authenticate(
                    null,
                    "Sign in to continue to the operations dashboard.",
                    ""
            );
            if(account==null)return;
            SessionManager.login(account);
        }

        Theme.setActive(workspaceTheme.id());
        OperationsWorkspaceFrame frame=new OperationsWorkspaceFrame(config);
        frame.setVisible(true);
    }
}
