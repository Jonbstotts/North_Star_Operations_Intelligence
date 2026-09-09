import com.wtm.ui.LoginRevealPolicy;

public final class LoginRevealPolicySmokeTest {
    private static void require(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }

    public static void main(String[] args){
        require(LoginRevealPolicy.revealProgress(-1)==0f,"negative time must clamp to zero");
        require(LoginRevealPolicy.revealProgress(0)==0f,"reveal must start transparent");
        require(LoginRevealPolicy.revealProgress(LoginRevealPolicy.DURATION_MILLIS)>=1f,
                "reveal must finish at duration");
        float previous=0f;
        for(int i=0;i<=100;i++){
            float p=LoginRevealPolicy.revealProgress(
                    LoginRevealPolicy.DURATION_MILLIS*i/100.0);
            require(p>=previous,"reveal easing must be monotonic");
            previous=p;
        }
        require(LoginRevealPolicy.verticalOffset(0f)>0,
                "reveal should begin with a small vertical settle offset");
        require(LoginRevealPolicy.verticalOffset(1f)==0,
                "reveal must finish at its resting position");
        System.out.println("LOGIN_REVEAL_POLICY_SMOKE_OK");
    }
}
