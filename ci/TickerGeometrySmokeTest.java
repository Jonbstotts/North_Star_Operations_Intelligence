import com.wtm.ui.ContinuousTickerGeometry;

public final class TickerGeometrySmokeTest {
    private static void require(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }

    public static void main(String[] args){
        for(int width:new int[]{640,900,1200,1600,1920}){
            for(int visible=2;visible<=8;visible++){
                for(int items=1;items<=20;items++){
                    ContinuousTickerGeometry.Layout layout=
                            ContinuousTickerGeometry.calculate(
                                    width,visible,items,150);

                    require(layout.slotWidth()>=150,
                            "slot width fell below minimum");
                    require(layout.cycleWidth()==items*layout.slotWidth(),
                            "cycle contains an artificial seam gap");
                    require(layout.copies()>=2,
                            "ticker must contain at least two cycles");
                    require(layout.trackWidth()==
                                    layout.cycleWidth()*layout.copies(),
                            "track width does not match complete cycles");
                    require(layout.trackWidth()>=
                                    layout.cycleWidth()+width,
                            "track cannot cover viewport at wrap boundary");
                }
            }
        }
        System.out.println("TICKER_GEOMETRY_SMOKE_OK");
    }
}
