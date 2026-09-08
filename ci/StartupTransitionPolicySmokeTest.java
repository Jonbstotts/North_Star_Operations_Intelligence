import com.wtm.ui.StartupTransitionPolicy;

/** Verifies the startup login reveal remains smooth, monotonic, and bounded. */
public final class StartupTransitionPolicySmokeTest {
    private StartupTransitionPolicySmokeTest(){}

    public static void main(String[] args){
        require(StartupTransitionPolicy.revealProgress(-1)==0f,"negative time was not clamped");
        require(StartupTransitionPolicy.revealProgress(
                StartupTransitionPolicy.LOGIN_REVEAL_MILLIS+100)==1f,
                "completed reveal was not clamped");

        float previous=-1f;
        for(int ms=0;ms<=StartupTransitionPolicy.LOGIN_REVEAL_MILLIS;ms+=8){
            float value=StartupTransitionPolicy.revealProgress(ms);
            require(value>=previous,"reveal easing is not monotonic");
            require(value>=0f&&value<=1f,"reveal easing escaped bounds");
            previous=value;
        }
        require(StartupTransitionPolicy.verticalOffset(0f)
                        ==StartupTransitionPolicy.FORM_TRAVEL_PIXELS,
                "initial reveal offset changed");
        require(StartupTransitionPolicy.verticalOffset(1f)==0,
                "completed reveal offset did not settle");
        require(StartupTransitionPolicy.TIMER_DELAY_MILLIS<=8,
                "startup reveal timer cadence regressed");

        System.out.println("STARTUP_TRANSITION_POLICY_SMOKE_OK");
    }

    private static void require(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }
}
